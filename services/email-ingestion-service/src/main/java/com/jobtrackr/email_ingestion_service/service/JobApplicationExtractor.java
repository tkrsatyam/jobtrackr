package com.jobtrackr.email_ingestion_service.service;

import com.jobtrackr.email_ingestion_service.dto.JobApplicationExtractionRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class JobApplicationExtractor {

    private static final Set<String> ATS_DOMAINS = Set.of(
            "myworkday.com", "greenhouse.io", "lever.co", "smartrecruiters.com",
            "ashbyhq.com", "icims.com", "taleo.net", "workablemail.com", "bamboohr.com"
    );

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

    private static final List<String> KNOWN_ROLES = List.of(
            "Core Java Developer", "Senior Java Developer", "Java Developer",
            "Senior Software Engineer", "Staff Software Engineer", "Principal Software Engineer",
            "Software Engineer", "Senior Backend Engineer", "Backend Engineer", "Backend Developer",
            "Senior Frontend Engineer", "Frontend Engineer", "Frontend Developer",
            "Senior Full Stack Engineer", "Full Stack Engineer", "Full Stack Developer",
            "DevOps Engineer", "Cloud Engineer", "Site Reliability Engineer", "SRE",
            "Data Engineer", "Senior Data Engineer", "Data Scientist", "ML Engineer",
            "Machine Learning Engineer", "Android Developer", "iOS Developer", "Mobile Developer",
            "QA Engineer", "Automation Engineer", "SDET", "Systems Engineer", "Solutions Architect",
            "Product Manager", "Associate Product Manager", "Engineering Manager"
    );

    private final ChatClient chatClient;

    public JobApplicationExtractor(@Autowired(required = false) ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public Optional<JobApplicationExtractionRecord> extract(String subject, String sender, String emailBody) {
        if (chatClient != null) {
            try {
                String prompt = """
                    You are an expert AI assistant that parses job application emails.
                    Analyze the following email and determine if it represents a legitimate job application confirmation, interview invitation, offer, or rejection.
                    
                    Sender: %s
                    Subject: %s
                    Body:
                    %s
                    
                    Extract the structured data:
                    - If isJobApplication is true:
                      - companyName: exact name of the company hiring (e.g. "Citi", "Stripe", "Google")
                      - roleTitle: exact job position title (e.g. "Core Java Developer", "Software Engineer")
                      - status: one of SAVED, APPLIED, PHONE_SCREEN, INTERVIEW, TECHNICAL_ROUND, HR_ROUND, OFFER, REJECTED.
                      - location: location if mentioned, else null
                      - jobUrl: application link if mentioned, else null
                      - recruiterName: name of recruiter if available, else null
                      - recruiterEmail: recruiter email if available, else null
                      - notes: concise 1-2 sentence summary of what this email says
                      - confidenceScore: number between 0.0 and 1.0 based on how confident you are that this is a genuine job application
                    - If isJobApplication is false:
                      - confidenceScore: 0.0
                    """.formatted(sender, subject, emailBody);

                JobApplicationExtractionRecord record = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .entity(JobApplicationExtractionRecord.class);

                if (record != null && record.isJobApplication() && record.confidenceScore() != null && record.confidenceScore() >= 0.6) {
                    return Optional.of(record);
                }
            } catch (Exception e) {
                log.warn("Spring AI extraction failed ({}), attempting heuristic fallback", e.getMessage());
            }
        }

        return heuristicFallback(subject, sender, emailBody);
    }

    public Optional<JobApplicationExtractionRecord> heuristicFallback(String subject, String sender, String emailBody) {
        if (subject == null) {
            return Optional.empty();
        }

        // Normalize Unicode formatting characters, en-dashes, and em-dashes
        subject = subject.replaceAll("[\\p{Cf}]", "").replace('–', '-').replace('—', '-').trim();

        String lowerSubject = subject.toLowerCase();
        String lowerBody = (emailBody != null) ? emailBody.toLowerCase() : "";
        String lowerSender = (sender != null) ? sender.toLowerCase() : "";

        // 1. Strict Sender Filtering (Newsletters / Alerts)
        if (lowerSender.contains("jobalerts-noreply@linkedin.com")
                || lowerSender.contains("naukrialerts@naukri.com")
                || lowerSender.contains("reviews@ambitionbox.com")
                || lowerSender.contains("updates@updates.indmoney.com")
                || lowerSender.contains("statements@transactions.indmoney.com")
                || lowerSender.contains("admissions@mastersunion.org")
                || lowerSender.contains("team@unacademy.com")
                || lowerSender.contains("hoyolab@e-mail.hoyoverse.com")) {
            log.debug("Rejecting non-application newsletter sender: {}", sender);
            return Optional.empty();
        }

        // 2. Strict Negative Keyword Filtering
        for (String neg : NEGATIVE_KEYWORDS) {
            if (lowerSubject.contains(neg) || lowerSender.contains(neg)) {
                log.debug("Rejecting email with negative keyword '{}': subject='{}', sender='{}'", neg, subject, sender);
                return Optional.empty();
            }
        }

        // 3. Strict Job Relevance Filtering
        boolean hasJobSignal = lowerSubject.contains("application")
                || lowerSubject.contains("applying")
                || lowerSubject.contains("job opportunity")
                || lowerSubject.contains("candidacy")
                || lowerSubject.contains("candidate")
                || lowerSubject.contains("interview")
                || lowerSubject.contains("job offer")
                || lowerSubject.contains("offer of employment")
                || lowerSubject.contains("offer letter")
                || lowerBody.contains("thank you for applying")
                || lowerBody.contains("thanks for applying")
                || lowerBody.contains("received your application")
                || lowerBody.contains("your application for")
                || lowerBody.contains("invite you to interview")
                || lowerBody.contains("review candidate for job application");

        if (!hasJobSignal) {
            return Optional.empty();
        }

        // If subject just contains the bare word "offer", verify it's an employment offer
        if (lowerSubject.contains("offer") && !lowerSubject.contains("job offer") && !lowerSubject.contains("offer of employment") && !lowerSubject.contains("offer letter")) {
            boolean hasEmploymentContext = lowerBody.contains("salary")
                    || lowerBody.contains("compensation")
                    || lowerBody.contains("joining date")
                    || lowerBody.contains("employment")
                    || lowerBody.contains("job title");
            if (!hasEmploymentContext) {
                return Optional.empty();
            }
        }

        // If subject contains bare word "interview", verify it's not a newsletter
        if (lowerSubject.contains("interview") && (lowerSubject.contains("newsletter") || lowerSubject.contains("problem") || lowerSubject.contains("weekly") || lowerSubject.contains("tips") || lowerSubject.contains("prep"))) {
            return Optional.empty();
        }

        // 4. Status Determination
        String status = "APPLIED";
        boolean isRejection = lowerSubject.contains("regret")
                || lowerSubject.contains("not moving forward")
                || lowerSubject.contains("not be moving forward")
                || lowerSubject.contains("unsuccessful")
                || lowerBody.contains("not moving forward")
                || lowerBody.contains("not be moving forward")
                || lowerBody.contains("won't be moving forward")
                || lowerBody.contains("pursue other")
                || lowerBody.contains("other candidates")
                || lowerBody.contains("not selected")
                || lowerBody.contains("regret to inform");

        boolean isOffer = lowerSubject.contains("offer of employment")
                || lowerSubject.contains("job offer")
                || lowerSubject.contains("offer letter")
                || lowerSubject.contains("congratulations on your offer")
                || lowerBody.contains("pleased to offer you")
                || lowerBody.contains("offer of employment")
                || lowerBody.contains("attached offer letter");

        boolean isInterview = lowerSubject.contains("interview invitation")
                || lowerSubject.contains("schedule your interview")
                || lowerSubject.contains("scheduled an interview")
                || lowerSubject.contains("complete questionnaire")
                || lowerSubject.contains("complete your ai interview")
                || lowerBody.contains("schedule an interview")
                || lowerBody.contains("invite you to interview")
                || lowerBody.contains("invitation to interview")
                || lowerBody.contains("complete questionnaire");

        if (isRejection) {
            status = "REJECTED";
        } else if (isOffer) {
            status = "OFFER";
        } else if (isInterview || lowerSubject.contains("interview")) {
            status = "INTERVIEW";
        }

        // 5. Company Name Extraction
        String company = extractCompanyName(sender, subject, lowerBody);

        // 6. Role Title Extraction
        String role = extractRoleTitle(subject, lowerBody);

        // 7. Dynamic Confidence Scoring
        double confidence = calculateConfidence(subject, sender, lowerBody, company, role, status);

        if (confidence < 0.60) {
            return Optional.empty();
        }

        String notes = "Extracted from email: " + subject;

        return Optional.of(new JobApplicationExtractionRecord(
                true,
                company,
                role,
                status,
                null,
                null,
                null,
                cleanEmailAddress(sender),
                notes,
                Math.round(confidence * 100.0) / 100.0
        ));
    }

    private String extractCompanyName(String sender, String subject, String lowerBody) {
        if (sender == null || sender.isBlank()) {
            return "Unknown Company";
        }

        // A. Check LinkedIn Easy Apply confirmation emails (e.g. "Satyam, your application was sent to Acme Corp")
        if (sender.toLowerCase().contains("linkedin.com")) {
            Pattern sentToPattern = Pattern.compile("(?i)(?:application was sent to|applied to)\\s+([^,–\\|]+)");
            Matcher sentToMatcher = sentToPattern.matcher(subject);
            if (sentToMatcher.find()) {
                String candidate = sentToMatcher.group(1).trim();
                if (candidate.length() > 1 && !candidate.equalsIgnoreCase("LinkedIn")) {
                    return candidate;
                }
            }
        }

        // B. Extract from sender display name (e.g. "Citi Human Resources <citi@myworkday.com>")
        int angleIdx = sender.indexOf('<');
        if (angleIdx > 0) {
            String rawDisplayName = sender.substring(0, angleIdx).trim().replaceAll("^\"|\"$", "");
            String cleaned = cleanDisplayName(rawDisplayName);
            if (!cleaned.isBlank()
                    && !cleaned.equalsIgnoreCase("No Reply")
                    && !cleaned.equalsIgnoreCase("Notifications")
                    && !cleaned.equalsIgnoreCase("Workday")
                    && !cleaned.equalsIgnoreCase("Myworkday")
                    && !cleaned.equalsIgnoreCase("Greenhouse")
                    && !cleaned.equalsIgnoreCase("Lever")) {
                return cleaned;
            }
        }

        // C. Check ATS domain local-parts (e.g. citi@myworkday.com -> Citi)
        String email = cleanEmailAddress(sender);
        if (email.contains("@")) {
            String localPart = email.substring(0, email.indexOf('@'));
            String domain = email.substring(email.indexOf('@') + 1).toLowerCase();

            for (String ats : ATS_DOMAINS) {
                if (domain.endsWith(ats) && !localPart.equalsIgnoreCase("no-reply") && !localPart.equalsIgnoreCase("notifications")) {
                    return capitalize(localPart);
                }
            }

            // Extract primary organization from domain (e.g. jobs@stripe.com -> Stripe)
            String[] parts = domain.split("\\.");
            for (String part : parts) {
                if (part.length() > 3
                        && !part.equals("mailers")
                        && !part.equals("emailers")
                        && !part.equals("connect")
                        && !part.equals("noreply")
                        && !part.equals("notifications")
                        && !part.equals("gmail")
                        && !part.equals("yahoo")
                        && !part.equals("outlook")) {
                    return capitalize(part);
                }
            }
        }

        return "Unknown Company";
    }

    private String cleanDisplayName(String displayName) {
        Pattern fromPattern = Pattern.compile("(?i).+?\\s+from\\s+([A-Za-z0-9\\s&]+)");
        Matcher fromMatcher = fromPattern.matcher(displayName);
        if (fromMatcher.find()) {
            displayName = fromMatcher.group(1).trim();
        }

        return displayName
                .replaceAll("(?i)\\s*(?:Human Resources|Talent Acquisition|Recruiting Team|Recruitment Team|Careers Team|Recruiting|Careers|Team|Jobs|HR Team|HR)\\b", "")
                .trim();
    }

    private String extractRoleTitle(String subject, String lowerBody) {
        // Pattern 1: Workday pattern e.g. "Review Candidate for Job Application: Satyam Kumar - 25886810 Core Java Developer (C112055873)"
        Pattern workdayPattern = Pattern.compile("(?i)(?:job application:|review candidate for job application:)\\s*[^\\-]+?\\s*-\\s*(?:\\d+\\s+)?([A-Za-z0-9\\s/+#\\.\\-]+?)(?:\\s*\\([A-Z0-9]+\\))?$");
        Matcher wdMatcher = workdayPattern.matcher(subject.trim());
        if (wdMatcher.find()) {
            String role = wdMatcher.group(1).trim();
            if (role.length() > 2) {
                return role;
            }
        }

        // Pattern 2: "Application for [Role] at [Company]" or "Applied for [Role]"
        Pattern standardPattern = Pattern.compile("(?i)(?:application(?:\\s+received|\\s+confirmation|\\s+submitted)?(?:\\s+for)?|applied(?:\\s+for|\\s+to)?|role:?|position:?)\\s+([A-Za-z0-9\\s/+#\\.\\-]+?)(?:\\s+(?:at|@|with|-|–|\\|)\\s+[A-Za-z0-9\\s&]+|$)");
        Matcher stdMatcher = standardPattern.matcher(subject.trim());
        if (stdMatcher.find()) {
            String candidate = stdMatcher.group(1).trim();
            if (candidate.length() > 2 && candidate.length() < 60) {
                return candidate;
            }
        }

        // Pattern 3: Scan against known roles in subject
        for (String knownRole : KNOWN_ROLES) {
            if (Pattern.compile("(?i)\\b" + Pattern.quote(knownRole) + "\\b").matcher(subject).find()) {
                return knownRole;
            }
        }

        // Pattern 4: Scan known roles in body preview
        for (String knownRole : KNOWN_ROLES) {
            if (Pattern.compile("(?i)\\b" + Pattern.quote(knownRole) + "\\b").matcher(lowerBody).find()) {
                return knownRole;
            }
        }

        return "Applicant Position";
    }

    private double calculateConfidence(String subject, String sender, String lowerBody, String company, String role, String status) {
        double score = 0.0;
        String lowerSubject = subject.toLowerCase();

        // 1. Subject signals
        if (lowerSubject.contains("thank you for applying") || lowerSubject.contains("application received") || lowerSubject.contains("job application:") || lowerSubject.contains("application was sent")) {
            score += 0.35;
        } else if (lowerSubject.contains("application for") || lowerSubject.contains("applied to") || lowerSubject.contains("applied for") || lowerSubject.contains("update regarding your application")) {
            score += 0.30;
        } else if (lowerSubject.contains("application") || lowerSubject.contains("applied")) {
            score += 0.20;
        }

        // Interview / Assessment signals
        if (lowerSubject.contains("interview invitation") || lowerSubject.contains("schedule your interview") || lowerSubject.contains("complete questionnaire") || lowerSubject.contains("complete your ai interview")) {
            score += 0.30;
        }

        // Offer signals
        if (lowerSubject.contains("job offer") || lowerSubject.contains("offer of employment") || lowerSubject.contains("offer letter")) {
            score += 0.40;
        }

        // Rejection / Offer / Interview status signals
        if ("REJECTED".equals(status)) {
            score += 0.25;
        } else if ("OFFER".equals(status)) {
            score += 0.30;
        } else if ("INTERVIEW".equals(status)) {
            score += 0.25;
        }

        // 2. ATS or Job Board sender bonus
        String email = cleanEmailAddress(sender).toLowerCase();
        boolean isKnownJobSender = false;
        for (String ats : ATS_DOMAINS) {
            if (email.contains(ats)) {
                isKnownJobSender = true;
                break;
            }
        }
        if (email.contains("linkedin.com") || email.contains("indeed.com") || email.contains("glassdoor.com")) {
            isKnownJobSender = true;
        }
        if (isKnownJobSender) {
            score += 0.25;
        }

        // 3. Verified role bonus
        if (role != null && !role.equals("Applicant Position") && !role.equals("Software Engineer")) {
            score += 0.20;
        } else if (role != null && !role.equals("Applicant Position")) {
            score += 0.10;
        }

        // 4. Verified company bonus
        if (company != null && !company.equals("Unknown Company")) {
            score += 0.15;
        }

        // 5. Body context signals
        if (lowerBody.contains("thank you for applying")
                || lowerBody.contains("thanks for applying")
                || lowerBody.contains("application has been received")
                || lowerBody.contains("review candidate for job application")
                || lowerBody.contains("application was sent")
                || lowerBody.contains("schedule an interview")
                || lowerBody.contains("invite you to interview")
                || lowerBody.contains("invitation to interview")) {
            score += 0.15;
        }

        return Math.min(0.98, score);
    }

    private String cleanEmailAddress(String sender) {
        if (sender == null) return "";
        Matcher matcher = Pattern.compile("<([^>]+)>").matcher(sender);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return sender.trim().replaceAll("[<>]", "");
    }

    private String capitalize(String text) {
        if (text == null || text.isBlank()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
