package com.jobtrackr.email_ingestion_service.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrackr.email_ingestion_service.dto.JobApplicationExtractionRecord;
import com.jobtrackr.email_ingestion_service.exception.AiExtractionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class JobApplicationExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final List<String> NEGATIVE_KEYWORDS = List.of(
            "loan", "credit card", "debit card", "pre-approved", "emi", "cashback",
            "flat % off", "discount", "deal of the day", "iphone", "shopping", "sale",
            "weekly problem", "problem #", "challenge #", "question #", "quiz #",
            "interview cake", "leetcode contest", "contest", "daily digest", "newsletter",
            "sweetens its offer", "takeover offer", "tender offer", "rights issue",
            "recharge", "bank statement", "account statement", "bill payment",
            "masters' union", "masters union", "pgp tbm", "pgp-tbm", "admission", "admissions",
            "mba round", "bpsc subscription", "unacademy", "hoyoverse", "hoyolab", "primogems",
            "jio platforms ipo", "pre-application", "shacket", "sloth bytes", "job alerts",
            "reviews & salaries"
    );

    private static final List<String> NEWSLETTER_SENDERS = List.of(
            "jobalerts-noreply@linkedin.com",
            "naukrialerts@naukri.com",
            "reviews@ambitionbox.com",
            "updates@updates.indmoney.com",
            "statements@transactions.indmoney.com",
            "admissions@mastersunion.org",
            "team@unacademy.com",
            "hoyolab@e-mail.hoyoverse.com"
    );

    private final ChatClient chatClient;

    public JobApplicationExtractor(@Autowired(required = false) ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public static boolean isAggregatedDigestOrAlert(String subject, String sender) {
        if (subject == null) return false;
        String lowerSubject = subject.toLowerCase();
        return lowerSubject.matches(".*applied for \\d+ jobs.*")
                || lowerSubject.matches(".*\\d+ jobs on \\d+.*")
                || lowerSubject.matches(".*\\d+ jobs? matching.*")
                || lowerSubject.matches(".*\\d+ new jobs?.*")
                || lowerSubject.contains("daily digest")
                || lowerSubject.contains("weekly digest")
                || lowerSubject.contains("job recommendations")
                || lowerSubject.contains("jobs you may be interested in")
                || lowerSubject.contains("recommended jobs")
                || lowerSubject.contains("top job picks")
                || lowerSubject.contains("new jobs in")
                || lowerSubject.contains("similar jobs");
    }

    public static boolean isNonJobEmail(String subject, String sender) {
        if (subject == null) return true;
        if (isAggregatedDigestOrAlert(subject, sender)) {
            return true;
        }
        String lowerSubject = subject.toLowerCase();
        String lowerSender = sender != null ? sender.toLowerCase() : "";

        for (String neg : NEGATIVE_KEYWORDS) {
            if (lowerSubject.contains(neg) || lowerSender.contains(neg)) {
                return true;
            }
        }
        for (String newsSender : NEWSLETTER_SENDERS) {
            if (lowerSender.contains(newsSender)) {
                return true;
            }
        }
        return false;
    }

    private static String sanitizeInput(String input) {
        if (input == null) return "";
        return input.replace("{", "[").replace("}", "]");
    }

    private static String extractJsonSubstring(String text) {
        if (text == null) return null;
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1).trim();
        }
        return text.trim();
    }

    private String cleanNotesUrl(String notes, String jobUrl) {
        if (notes == null || notes.isBlank()) {
            return notes;
        }
        if (jobUrl != null && !jobUrl.isBlank()) {
            String cleaned = notes.replace("• Job URL: " + jobUrl, "")
                    .replace("Job URL: " + jobUrl, "")
                    .replace(jobUrl, "");
            return cleaned.replaceAll("(?m)^\\s*\\n", "").trim();
        }
        return notes;
    }

    public Optional<JobApplicationExtractionRecord> extract(String subject, String sender, String emailBody) {
        if (isNonJobEmail(subject, sender)) {
            log.info("Skipping non-job email or aggregated digest: subject='{}', sender='{}'", subject, sender);
            return Optional.empty();
        }

        if (chatClient == null) {
            log.warn("ChatClient is not available. Skipping AI extraction for subject: '{}'", subject);
            return Optional.empty();
        }

        String prompt = """
            You are an expert AI assistant that parses job application emails.
            Analyze the following email and determine if it represents a legitimate job application confirmation, interview invitation, offer, or rejection.

            Sender: %s
            Subject: %s
            Body:
            %s

            Extract the structured data in JSON format with these exact keys:
            {
              "isJobApplication": boolean,
              "companyName": string or null,
              "roleTitle": string or null,
              "status": string,
              "location": string or null,
              "jobUrl": string or null,
              "recruiterName": string or null,
              "recruiterEmail": string or null,
              "notes": string or null,
              "confidenceScore": number
            }

            Extraction Rules:
            - If isJobApplication is true:
              - companyName: exact name of the hiring employer (e.g. if the email is from Indeed or LinkedIn, find the actual hiring company mentioned in the body like "Novo Tree Minds" or "Stripe", NOT "Indeed Apply" or "LinkedIn").
              - roleTitle: exact job position title including level numbers and roman numerals (e.g. "Software Engineer I", "Software Engineer 2", "Core Java Developer").
              - status: determine the exact status according to these rules:
                * REJECTED: email states application is not moving forward, candidate not selected, pursuing other candidates, or regret to inform/unsuccessful.
                * OFFER: formal job offer, offer letter, employment offer, or compensation package details.
                * ACCEPTED: welcome to the team, signing confirmation, or onboarding after offer acceptance.
                * TECHNICAL_ROUND: coding challenge, technical assessment, HackerRank, CodeSignal, take-home assignment, live coding, or technical interview.
                * HR_ROUND: HR round, culture fit interview, behavioral questions, or final managerial discussion.
                * PHONE_SCREEN: recruiter phone screen, introductory talent call, or screening chat.
                * INTERVIEW: general interview invitation or unspecified interview schedule. Do NOT mark questionnaires, candidate portal profile setup, or Workday task notifications as INTERVIEW (they should be APPLIED).
                * APPLIED: application submission confirmation, questionnaire task, candidate portal setup, or "thank you for applying".
              - location: candidate or job location if mentioned, else null.
              - jobUrl: job posting or application status URL if found in the email, else null.
              - recruiterName: name of recruiter if available, else null.
              - recruiterEmail: recruiter email if available, else null.
              - notes: concise, informative 1-2 sentence summary of what this email says, mentioning the role and company. Do NOT include or repeat the job URL in notes.
              - confidenceScore: number between 0.0 and 1.0 based on how confident you are that this is a genuine job application.
            - If isJobApplication is false:
              - Mark isJobApplication as false for aggregated job alerts, application digest summaries (e.g. "You applied for 7 jobs", weekly summaries), newsletters, promotional emails, or general job recommendations.
              - confidenceScore: 0.0

            Return ONLY a valid JSON object matching the schema above. Do not wrap in markdown or add explanations.
            """.formatted(sanitizeInput(sender), sanitizeInput(subject), sanitizeInput(emailBody));

        String rawResponse;
        try {
            rawResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI model execution failed for subject '{}': {}", subject, e.getMessage(), e);
            throw new AiExtractionException("AI model execution failed: " + e.getMessage(), e);
        }

        if (rawResponse == null || rawResponse.isBlank()) {
            log.warn("AI model returned empty response for subject: '{}'", subject);
            return Optional.empty();
        }

        try {
            String jsonPayload = extractJsonSubstring(rawResponse);
            JobApplicationExtractionRecord record = OBJECT_MAPPER.readValue(jsonPayload, JobApplicationExtractionRecord.class);

            if (record != null && record.isJobApplication() && record.confidenceScore() != null && record.confidenceScore() >= 0.6) {
                String sanitizedNotes = cleanNotesUrl(record.notes(), record.jobUrl());
                return Optional.of(new JobApplicationExtractionRecord(
                        record.isJobApplication(),
                        record.companyName(),
                        record.roleTitle(),
                        record.status(),
                        record.location(),
                        record.jobUrl(),
                        record.recruiterName(),
                        record.recruiterEmail(),
                        sanitizedNotes,
                        record.confidenceScore()
                ));
            }
            log.info("AI determined email is not a job application or confidence too low: subject='{}'", subject);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to parse AI JSON response for subject '{}': {}", subject, e.getMessage());
            throw new AiExtractionException("Failed to parse AI JSON response: " + e.getMessage(), e);
        }
    }
}
