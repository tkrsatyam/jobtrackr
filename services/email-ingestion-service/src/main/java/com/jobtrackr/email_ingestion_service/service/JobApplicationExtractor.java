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

    private static final List<String> NEWSLETTER_SENDERS = List.of(
            "jobalerts-noreply@linkedin.com",
            "naukrialerts@naukri.com",
            "reviews@ambitionbox.com",
            "updates@updates.indmoney.com",
            "statements@transactions.indmoney.com",
            "admissions@mastersunion.org",
            "team@unacademy.com",
            "hoyolab@e-mail.hoyoverse.com",
            "careercamp.codingninjas.com",
            "novoresume.net",
            "match.indeed.com",
            "interviewcake.com"
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
                || lowerSubject.contains("similar jobs")
                || lowerSubject.contains("see what employees have to say")
                || lowerSubject.contains("reviews & salaries")
                || lowerSubject.contains("check out jobs applied by other")
                || lowerSubject.contains("interview cake")
                || lowerSubject.contains("leetcode contest")
                || lowerSubject.contains("weekly problem")
                || lowerSubject.matches(".*(problem|challenge|question|quiz) #\\d+.*");
    }

    public static boolean isNonJobEmail(String subject, String sender) {
        if (subject == null) return true;
        if (isAggregatedDigestOrAlert(subject, sender)) {
            return true;
        }

        String lowerSender = sender != null ? sender.toLowerCase() : "";
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
            Analyze the following email and determine if it represents a legitimate job application confirmation, interview invitation, offer, rejection, or recruiter communication.

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
              "confidenceScore": number,
              "rejectionReason": string or null
            }

            Extraction Rules:
            - If isJobApplication is true:
              - companyName: exact name of the hiring employer (e.g. if the email is from Indeed or LinkedIn, find the actual hiring company mentioned in the body like "Novo Tree Minds" or "Stripe", NOT "Indeed Apply" or "LinkedIn"). If the email is sent by the applicant to a company or recruiter, extract the target hiring employer from the email body or recipient (NOT the applicant's name).
              - roleTitle: exact job position title including level numbers and roman numerals (e.g. "Software Engineer I", "Software Engineer 2", "Core Java Developer"). If no role title is mentioned in the email, use "Role Not Specified" (do NOT invent placeholder titles like "Applicant Position").
              - status: determine the exact status according to these rules:
                * REJECTED: email states application is not moving forward, candidate not selected, pursuing other candidates, or regret to inform/unsuccessful.
                * OFFER: formal job offer, offer letter, employment offer, or compensation package details.
                * ACCEPTED: welcome to the team, signing confirmation, or onboarding after offer acceptance.
                * TECHNICAL_ROUND: coding challenge, technical assessment, HackerRank, CodeSignal, take-home assignment, live coding, technical interview, AI interview, or automated assessment/screening (such as micro1, HireVue, Karat, etc.).
                * HR_ROUND: HR round, culture fit interview, behavioral questions, or final managerial discussion.
                * PHONE_SCREEN: recruiter phone screen, introductory talent call, preliminary screening, or screening chat.
                * INTERVIEW: interview invitation or scheduled rounds with human interviewers (recruiter, hiring manager, panel). Do NOT mark automated AI interviews, coding assessments, questionnaires, candidate portal profile setup, or Workday task notifications as INTERVIEW.
                * APPLIED: application submission confirmation, questionnaire task, candidate portal setup, or "thank you for applying".
              - location: candidate or job location if mentioned, else null.
              - jobUrl: job posting or application status URL if found in the email, else null.
              - recruiterName: name of recruiter if available, else null.
              - recruiterEmail: recruiter email if available, else null.
              - notes: concise, informative 1-2 sentence summary of what this email says, mentioning the role and company. Do NOT include or repeat the job URL in notes.
              - rejectionReason: null (or brief rationale if confidence is moderate).
            - confidenceScore: calibrate as a realistic continuous probability between 0.00 and 1.00:
              * 0.90 to 1.00: Definitive direct communication regarding a job application (e.g. application received from ATS/company, interview invite, formal offer, or rejection letter).
              * 0.60 to 0.89: Likely job application communication (e.g. recruiter reaching out regarding candidate profile/resume, assessment links, portal profile update requests).
              * 0.30 to 0.59: Ambiguous or borderline (e.g. generic talent community updates, career fair invitations, university placement notices).
              * 0.00 to 0.29: Definitely NOT an individual job application (e.g. marketing newsletters, general job boards, promotional spam, shopping offers, bank statements).
            - If isJobApplication is false:
              - Mark isJobApplication as false for promotional emails, bank/credit card notices, shopping deals, aggregated digests, or course bootcamps.
              - Set companyName, roleTitle, location, jobUrl, recruiterName, recruiterEmail, notes to null.
              - status: "APPLIED"
              - confidenceScore: assign a realistic low score (typically 0.00 to 0.25).
              - rejectionReason: concise explanation of why this is not a job application (e.g. "Commercial promotional email", "Course bootcamp ad", "Aggregated job digest").

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

            if (record != null) {
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
                        record.confidenceScore(),
                        record.rejectionReason()
                ));
            }
            log.info("AI returned null record for subject='{}'", subject);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to parse AI JSON response for subject '{}': {}", subject, e.getMessage());
            throw new AiExtractionException("Failed to parse AI JSON response: " + e.getMessage(), e);
        }
    }
}
