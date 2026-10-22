package com.jobtrackr.email_ingestion_service.service;

import com.jobtrackr.email_ingestion_service.dto.JobApplicationExtractionRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class JobApplicationExtractor {

    private final ChatClient chatClient;

    public JobApplicationExtractor(@Autowired(required = false) ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public Optional<JobApplicationExtractionRecord> extract(String subject, String sender, String emailBody) {
        if (chatClient != null) {
            try {
                String prompt = """
                    You are an expert AI assistant that parses job application emails.
                    Analyze the following email and determine if it represents a job application confirmation, interview invitation, offer, or rejection.
                    
                    Sender: %s
                    Subject: %s
                    Body:
                    %s
                    
                    Extract the structured data:
                    - If isJobApplication is true:
                      - companyName: name of the company hiring (e.g. "Stripe", "Google", "Acme Inc")
                      - roleTitle: job position title (e.g. "Software Engineer", "Backend Developer")
                      - status: one of SAVED, APPLIED, PHONE_SCREEN, INTERVIEW, TECHNICAL_ROUND, HR_ROUND, OFFER, REJECTED. Default to APPLIED for initial confirmation.
                      - location: location if mentioned, else null
                      - jobUrl: application link if mentioned, else null
                      - recruiterName: name of recruiter if available, else null
                      - recruiterEmail: recruiter email if available, else null
                      - notes: concise 1-2 sentence summary of what this email says
                      - confidenceScore: number between 0.0 and 1.0
                    - If isJobApplication is false:
                      - confidenceScore: 0.0
                    """.formatted(sender, subject, emailBody);

                JobApplicationExtractionRecord record = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .entity(JobApplicationExtractionRecord.class);

                if (record != null && record.isJobApplication() && record.confidenceScore() != null && record.confidenceScore() >= 0.5) {
                    return Optional.of(record);
                }
            } catch (Exception e) {
                log.warn("Spring AI extraction failed ({}), attempting heuristic fallback", e.getMessage());
            }
        }

        return heuristicFallback(subject, sender, emailBody);
    }

    private Optional<JobApplicationExtractionRecord> heuristicFallback(String subject, String sender, String emailBody) {
        if (subject == null) {
            return Optional.empty();
        }

        String lowerSubject = subject.toLowerCase();
        String lowerBody = (emailBody != null) ? emailBody.toLowerCase() : "";

        boolean isJobRelated = lowerSubject.contains("application")
                || lowerSubject.contains("applying")
                || lowerSubject.contains("interview")
                || lowerSubject.contains("offer")
                || lowerSubject.contains("job opportunity")
                || lowerSubject.contains("candidacy")
                || lowerBody.contains("thank you for applying")
                || lowerBody.contains("interview invitation")
                || lowerBody.contains("job application");

        if (!isJobRelated) {
            return Optional.empty();
        }

        String status = "APPLIED";
        if (lowerSubject.contains("offer") || lowerBody.contains("offer letter")) {
            status = "OFFER";
        } else if (lowerSubject.contains("interview") || lowerBody.contains("schedule an interview")) {
            status = "INTERVIEW";
        } else if (lowerSubject.contains("regret") || lowerSubject.contains("not moving forward") || lowerBody.contains("we will not be moving forward")) {
            status = "REJECTED";
        }

        // Try extracting company and role from subject e.g. "Application for Software Engineer at Stripe"
        Pattern pattern = Pattern.compile("(?i)(?:application for|applied to|role:?)\\s+([^@\\-\\–\\|]+?)\\s+(?:at|@|with)\\s+([^@\\-\\–\\|\\.\\,\\!]+)");
        Matcher matcher = pattern.matcher(subject);

        String role = "Software Engineer";
        String company = "Unknown Company";

        if (matcher.find()) {
            role = matcher.group(1).trim();
            company = matcher.group(2).trim();
        } else {
            // Check "Thank you for applying to [Company]"
            Pattern companyPattern = Pattern.compile("(?i)applying to\\s+([A-Za-z0-9\\s&]+?)(?:\\s+for|\\s*\\.|\\s*\\!)");
            Matcher compMatcher = companyPattern.matcher(subject + " " + lowerBody);
            if (compMatcher.find()) {
                company = compMatcher.group(1).trim();
            } else if (sender != null && sender.contains("@")) {
                String domain = sender.substring(sender.indexOf('@') + 1);
                company = domain.replace(".com", "").replace(".io", "").replace(".co", "");
                if (company.length() > 1) {
                    company = Character.toUpperCase(company.charAt(0)) + company.substring(1);
                }
            }
        }

        return Optional.of(new JobApplicationExtractionRecord(
                true,
                company,
                role,
                status,
                null,
                null,
                null,
                sender,
                "Extracted from email: " + subject,
                0.85
        ));
    }
}
