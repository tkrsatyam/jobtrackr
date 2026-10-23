package com.jobtrackr.email_ingestion_service.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrackr.email_ingestion_service.dto.JobApplicationExtractionRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class JobApplicationExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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

    private static final Set<String> ATS_DOMAINS = Set.of(
            "myworkday.com", "greenhouse.io", "lever.co", "smartrecruiters.com",
            "ashbyhq.com", "icims.com", "taleo.net", "workablemail.com", "bamboohr.com", "recooty.com"
    );

    private static final Set<String> JOB_PLATFORMS = Set.of(
            "indeed apply", "indeed", "linkedin", "naukri", "instahyre", "glassdoor",
            "recooty", "workday", "myworkday", "greenhouse", "lever", "smartrecruiters",
            "notifications", "no reply", "recruiting team", "talent acquisition", "careers team"
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
            "Software Engineer I", "Software Engineer II", "Software Engineer III", "Software Engineer IV",
            "Software Engineer 1", "Software Engineer 2", "Software Engineer 3",
            "Senior Software Engineer", "Staff Software Engineer", "Principal Software Engineer",
            "Associate Software Engineer", "Software Engineer",
            "Senior Backend Engineer", "Backend Engineer 2", "Backend Engineer 1", "Backend Engineer", "Backend Developer",
            "Senior Frontend Engineer", "Frontend Engineer 2", "Frontend Engineer 1", "Frontend Engineer", "Frontend Developer",
            "Senior Full Stack Engineer", "Full Stack Engineer 2", "Full Stack Engineer 1", "Full Stack Engineer", "Full Stack Developer",
            "SDE I", "SDE II", "SDE III", "SDE 1", "SDE 2", "SDE 3",
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
        if (isAggregatedDigestOrAlert(subject, sender)) {
            log.info("Skipping aggregated job digest email: subject='{}'", subject);
            return Optional.empty();
        }

        if (chatClient != null) {
            try {
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

                String rawResponse = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                if (rawResponse != null && !rawResponse.isBlank()) {
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
                }
            } catch (Exception e) {
                log.warn("Spring AI extraction failed ({}), attempting heuristic fallback", e.getMessage());
            }
        }

        return heuristicFallback(subject, sender, emailBody);
    }

    public Optional<JobApplicationExtractionRecord> heuristicFallback(String subject, String sender, String emailBody) {
        if (subject == null || isAggregatedDigestOrAlert(subject, sender)) {
            return Optional.empty();
        }

        // Normalize Unicode formatting characters, en-dashes, and em-dashes
        subject = subject.replaceAll("[\\p{Cf}]", "").replace('–', '-').replace('—', '-').trim();

        String lowerSubject = subject.toLowerCase();
        String body = (emailBody != null) ? emailBody : "";
        String lowerBody = body.toLowerCase();
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
                || lowerSubject.contains("applied")
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
                || lowerBody.contains("review candidate for job application")
                || lowerBody.contains("application submitted");

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

        // If subject contains bare word "interview", verify it's not a newsletter or prep question
        if (lowerSubject.contains("interview") && (lowerSubject.contains("newsletter") || lowerSubject.contains("problem") || lowerSubject.contains("weekly") || lowerSubject.contains("tips") || lowerSubject.contains("prep"))) {
            return Optional.empty();
        }

        // 4. Status Determination
        String status = "APPLIED";
        boolean isRejection = lowerSubject.contains("regret")
                || lowerSubject.contains("not moving forward")
                || lowerSubject.contains("not be moving forward")
                || lowerSubject.contains("unsuccessful")
                || lowerSubject.contains("not selected")
                || lowerBody.contains("not moving forward")
                || lowerBody.contains("not be moving forward")
                || lowerBody.contains("won't be moving forward")
                || lowerBody.contains("pursue other candidates")
                || lowerBody.contains("pursuing other candidates")
                || lowerBody.contains("pursue other")
                || lowerBody.contains("other candidates")
                || lowerBody.contains("not selected")
                || lowerBody.contains("regret to inform")
                || lowerBody.contains("position has been filled")
                || lowerBody.contains("decided not to move forward")
                || lowerBody.contains("unable to offer you an interview");

        boolean isOffer = lowerSubject.contains("offer of employment")
                || lowerSubject.contains("job offer")
                || lowerSubject.contains("offer letter")
                || lowerSubject.contains("congratulations on your offer")
                || lowerBody.contains("pleased to offer you")
                || lowerBody.contains("offer of employment")
                || lowerBody.contains("attached offer letter")
                || lowerBody.contains("extend an offer of employment")
                || lowerBody.contains("congratulations on receiving this offer");

        boolean isAccepted = lowerSubject.contains("welcome to the team")
                || lowerSubject.contains("onboarding details")
                || lowerBody.contains("welcome to the team")
                || lowerBody.contains("signed offer letter")
                || lowerBody.contains("confirmed your acceptance");

        boolean isTechnicalRound = lowerSubject.contains("coding assessment")
                || lowerSubject.contains("online assessment")
                || lowerSubject.contains("technical assessment")
                || lowerSubject.contains("hackerrank")
                || lowerSubject.contains("codesignal")
                || lowerSubject.contains("technical test")
                || lowerSubject.contains("coding test")
                || lowerSubject.contains("technical challenge")
                || lowerSubject.contains("oa invitation")
                || lowerBody.contains("coding assessment")
                || lowerBody.contains("online assessment")
                || lowerBody.contains("technical assessment")
                || lowerBody.contains("hackerrank.com")
                || lowerBody.contains("codesignal.com")
                || lowerBody.contains("take-home assignment")
                || lowerBody.contains("take home assignment")
                || lowerBody.contains("live coding session");

        boolean isHrRound = lowerSubject.contains("hr round")
                || lowerSubject.contains("hr interview")
                || lowerSubject.contains("culture fit")
                || lowerSubject.contains("behavioral interview")
                || lowerBody.contains("hr round")
                || lowerBody.contains("culture fit interview")
                || lowerBody.contains("behavioral round");

        boolean isPhoneScreen = lowerSubject.contains("phone screen")
                || lowerSubject.contains("recruiter call")
                || lowerSubject.contains("screening call")
                || lowerSubject.contains("introductory call")
                || lowerSubject.contains("intro call")
                || lowerBody.contains("recruiter phone screen")
                || lowerBody.contains("short phone screen")
                || lowerBody.contains("introductory call with our recruiter")
                || lowerBody.contains("screening call with");

        boolean isInterview = lowerSubject.contains("interview invitation")
                || lowerSubject.contains("schedule your interview")
                || lowerSubject.contains("scheduled an interview")
                || lowerSubject.contains("complete your ai interview")
                || lowerBody.contains("schedule an interview")
                || lowerBody.contains("invite you to interview")
                || lowerBody.contains("invitation to interview")
                || lowerBody.contains("complete your ai interview")
                || lowerBody.contains("complete questionnaire");

        if (isRejection) {
            status = "REJECTED";
        } else if (isOffer) {
            status = "OFFER";
        } else if (isAccepted) {
            status = "ACCEPTED";
        } else if (isTechnicalRound) {
            status = "TECHNICAL_ROUND";
        } else if (isHrRound) {
            status = "HR_ROUND";
        } else if (isPhoneScreen) {
            status = "PHONE_SCREEN";
        } else if (isInterview || lowerSubject.contains("interview")) {
            status = "INTERVIEW";
        } else {
            status = "APPLIED";
        }

        // 5. Company Name Extraction
        String company = extractCompanyName(sender, subject, body);

        // 6. Role Title Extraction
        String role = extractRoleTitle(subject, body);

        // 7. Location Extraction
        String location = extractLocation(subject, body);

        // 8. Job URL Extraction
        String jobUrl = extractJobUrl(body);

        // 9. Dynamic Confidence Scoring
        double confidence = calculateConfidence(subject, sender, lowerBody, company, role, status, jobUrl, location);

        if (confidence < 0.60) {
            return Optional.empty();
        }

        // 10. Meaningful Structured Notes
        String notes = buildNotes(subject, sender, company, role, location, jobUrl, body);

        return Optional.of(new JobApplicationExtractionRecord(
                true,
                company,
                role,
                status,
                location,
                jobUrl,
                null,
                cleanEmailAddress(sender),
                notes,
                Math.round(confidence * 100.0) / 100.0
        ));
    }

    private String extractCompanyName(String sender, String subject, String body) {
        if (sender == null || sender.isBlank()) {
            return "Unknown Company";
        }

        String lowerSender = sender.toLowerCase();

        // A. Check Indeed Apply confirmation emails (body has: "The following items were sent to [Company]" or "[Role]\n[Company] - [Location]")
        if (lowerSender.contains("indeed")) {
            Pattern sentToPattern = Pattern.compile("(?i)(?:sent to|submitted to|items were sent to)\\s+([A-Za-z0-9\\s&,\\.\\-]+?)(?:\\.|\\s*Good luck|\\s*Thank you|\\s*\\n)");
            Matcher sentToMatcher = sentToPattern.matcher(body);
            if (sentToMatcher.find()) {
                String candidate = sentToMatcher.group(1).trim();
                if (isValidCompanyName(candidate)) {
                    return candidate;
                }
            }

            Pattern roleCompanyPattern = Pattern.compile("(?i)(?:Developer|Engineer|Architect|Manager|Analyst|Consultant|Scientist)\\s*\\n+([A-Za-z0-9\\s&,\\.\\-]+?)\\s*-\\s*(?:[A-Za-z0-9\\s,]+(?:India|\\d{5,6}|Remote|Hybrid|On-site))");
            Matcher rcMatcher = roleCompanyPattern.matcher(body);
            if (rcMatcher.find()) {
                String candidate = rcMatcher.group(1).trim();
                if (isValidCompanyName(candidate)) {
                    return candidate;
                }
            }
        }

        // B. Check ATS confirmation body pattern: "applying for the [Role] position at [Company]" or "role at [Company]"
        Pattern atsBodyPattern = Pattern.compile("(?i)(?:thank(?:s|\\s+you)\\s+for\\s+applying\\s+(?:for|to)\\s+(?:the\\s+)?|applied\\s+(?:for|to)\\s+(?:the\\s+)?|applying\\s+(?:for|to)\\s+(?:the\\s+)?)[A-Za-z0-9\\s/+#\\.\\-]+?(?:\\s*-\\s*(?:Job\\s*ID|Req|Position\\s*ID|ID)[:\\s]*[A-Za-z0-9]+)?\\s+(?:role|position|job)\\s+at\\s+([A-Za-z0-9\\s&\\.\\-]+?)(?:\\.|!|\\s*\\n|\\s*We\\b|$)");
        Matcher atsBodyMatcher = atsBodyPattern.matcher(body);
        if (atsBodyMatcher.find()) {
            String candidate = atsBodyMatcher.group(1).trim();
            if (isValidCompanyName(candidate)) {
                return candidate;
            }
        }

        // C. Check LinkedIn Easy Apply confirmation emails (e.g. "Satyam, your application was sent to Acme Corp")
        if (lowerSender.contains("linkedin.com")) {
            Pattern sentToPattern = Pattern.compile("(?i)(?:application was sent to|applied to)\\s+([^,–\\|]+)");
            Matcher sentToMatcher = sentToPattern.matcher(subject);
            if (sentToMatcher.find()) {
                String candidate = sentToMatcher.group(1).trim();
                if (isValidCompanyName(candidate)) {
                    return candidate;
                }
            }
        }

        // D. Extract from sender display name (e.g. "Citi Human Resources <citi@myworkday.com>")
        int angleIdx = sender.indexOf('<');
        if (angleIdx > 0) {
            String rawDisplayName = sender.substring(0, angleIdx).trim().replaceAll("^\"|\"$", "");
            String cleaned = cleanDisplayName(rawDisplayName);
            if (isValidCompanyName(cleaned)) {
                return cleaned;
            }
        }

        // E. Check ATS domain local-parts (e.g. citi@myworkday.com -> Citi)
        String email = cleanEmailAddress(sender);
        if (email.contains("@")) {
            String localPart = email.substring(0, email.indexOf('@'));
            String domain = email.substring(email.indexOf('@') + 1).toLowerCase();

            for (String ats : ATS_DOMAINS) {
                if (domain.endsWith(ats) && !localPart.equalsIgnoreCase("no-reply") && !localPart.equalsIgnoreCase("notifications")) {
                    String candidate = capitalize(localPart);
                    if (isValidCompanyName(candidate)) {
                        return candidate;
                    }
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
                        && !part.equals("outlook")
                        && !part.equals("indeed")
                        && !part.equals("recooty")
                        && !part.equals("linkedin")) {
                    return capitalize(part);
                }
            }
        }

        return "Unknown Company";
    }

    private boolean isValidCompanyName(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() < 2 || candidate.length() > 60) {
            return false;
        }
        String lower = candidate.trim().toLowerCase();
        for (String platform : JOB_PLATFORMS) {
            if (lower.equals(platform) || lower.startsWith(platform + " ")) {
                return false;
            }
        }
        return !lower.equals("no reply") && !lower.equals("notifications");
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

    private String extractRoleTitle(String subject, String body) {
        // Pattern 1: Workday pattern e.g. "Review Candidate for Job Application: Satyam Kumar - 25886810 Core Java Developer (C112055873)"
        Pattern workdayPattern = Pattern.compile("(?i)(?:job application:|review candidate for job application:)\\s*[^\\-]+?\\s*-\\s*(?:\\d+\\s+)?([A-Za-z0-9\\s/+#\\.\\-]+?)(?:\\s*\\([A-Z0-9]+\\))?$");
        Matcher wdMatcher = workdayPattern.matcher(subject.trim());
        if (wdMatcher.find()) {
            String role = sanitizeRole(wdMatcher.group(1));
            if (isValidRole(role)) {
                return role;
            }
        }

        // Pattern 2: ATS body/subject pattern (Recooty, Greenhouse, MetLife, etc.):
        // "applying for the Java Developer position at Novo Tree Minds"
        // "applying to the Software Engineer I - Job ID:20903 role at MetLife"
        Pattern atsRolePattern = Pattern.compile("(?i)(?:thank(?:s|\\s+you)\\s+for\\s+applying\\s+(?:for|to)\\s+(?:the\\s+)?|applied\\s+(?:for|to)\\s+(?:the\\s+)?|applying\\s+(?:for|to)\\s+(?:the\\s+)?)([A-Za-z0-9\\s/+#\\.\\-]+?)(?:\\s*-\\s*(?:Job\\s*ID|Req|Position\\s*ID|ID)[:\\s]*[A-Za-z0-9]+)?\\s+(?:role|position|job)\\s+at\\s+");
        Matcher atsBodyMatcher = atsRolePattern.matcher(body);
        if (atsBodyMatcher.find()) {
            String role = sanitizeRole(atsBodyMatcher.group(1));
            if (isValidRole(role)) {
                return role;
            }
        }

        Matcher atsSubjectMatcher = atsRolePattern.matcher(subject);
        if (atsSubjectMatcher.find()) {
            String role = sanitizeRole(atsSubjectMatcher.group(1));
            if (isValidRole(role)) {
                return role;
            }
        }

        // Pattern 3: Indeed subject pattern: "Indeed Application: Java Developer" or "Indeed Application: Software Engineer 2 (Bangalore, India)"
        Pattern indeedSubjectPattern = Pattern.compile("(?i)indeed\\s+application:\\s*([A-Za-z0-9\\s/+#\\.\\-]+)");
        Matcher indMatcher = indeedSubjectPattern.matcher(subject.trim());
        if (indMatcher.find()) {
            String role = sanitizeRole(indMatcher.group(1));
            if (isValidRole(role)) {
                return role;
            }
        }

        // Pattern 4: Standard pattern: "Application for [Role] at [Company]"
        Pattern standardPattern = Pattern.compile("(?i)(?:application(?:\\s+confirmation|\\s+submitted)?(?:\\s+for)?|applied(?:\\s+for|\\s+to)?|role:?|position:?)\\s+([A-Za-z0-9\\s/+#\\.\\-]+?)(?:\\s+(?:at|@|with|-|–|\\|)\\s+[A-Za-z0-9\\s&]+|$)");
        Matcher stdMatcher = standardPattern.matcher(subject.trim());
        if (stdMatcher.find()) {
            String candidate = sanitizeRole(stdMatcher.group(1));
            if (isValidRole(candidate)) {
                return candidate;
            }
        }

        // Pattern 5: Scan known roles in subject (ordered with specific levels first)
        for (String knownRole : KNOWN_ROLES) {
            if (Pattern.compile("(?i)\\b" + Pattern.quote(knownRole) + "\\b").matcher(subject).find()) {
                return knownRole;
            }
        }

        // Pattern 6: Scan known roles in body preview
        for (String knownRole : KNOWN_ROLES) {
            if (Pattern.compile("(?i)\\b" + Pattern.quote(knownRole) + "\\b").matcher(body).find()) {
                return knownRole;
            }
        }

        return "Applicant Position";
    }

    private String sanitizeRole(String rawRole) {
        if (rawRole == null) return "";
        return rawRole
                .replaceAll("(?i)\\s*\\([^)]*(?:India|Remote|Hybrid|On-site|USA|UK|Bangalore|Pune|Hyderabad|Noida|Gurgaon|Delhi|Mumbai|Req|Job\\s*ID|ID)[^)]*\\)", "")
                .replaceAll("(?i)\\s*\\([A-Z0-9]+\\)", "")
                .replaceAll("(?i)\\s*-\\s*(?:Job\\s*ID|Req|Position\\s*ID|ID)[:\\s]*[A-Za-z0-9]+", "")
                .trim();
    }

    private boolean isValidRole(String role) {
        if (role == null || role.length() < 3 || role.length() > 60) {
            return false;
        }
        String lower = role.trim().toLowerCase();
        if (lower.equals("received")
                || lower.equals("confirmation")
                || lower.equals("submitted")
                || lower.equals("thank you")
                || lower.equals("position")
                || lower.equals("role")
                || lower.equals("job application")
                || lower.startsWith("was sent to")
                || lower.startsWith("sent to")
                || lower.startsWith("your application")) {
            return false;
        }
        return true;
    }

    private String extractLocation(String subject, String body) {
        // A. From Indeed body: "Novo Tree Minds - Pune, Maharashtra, India 411001"
        Pattern indeedLoc = Pattern.compile("(?i)(?:-\\s*|\\bin\\s+)([A-Za-z0-9\\s,]+(?:India|\\d{5,6}|Remote|Hybrid|On-site))");
        Matcher indMatcher = indeedLoc.matcher(body);
        if (indMatcher.find()) {
            String loc = indMatcher.group(1).trim();
            if (loc.length() >= 4 && loc.length() < 60) {
                return loc;
            }
        }

        // B. From subject/role parenthetical: "Software Engineer 2 (Bangalore, India)"
        Pattern parenLoc = Pattern.compile("(?i)\\(([^)]*(?:India|Remote|Hybrid|On-site|USA|UK|Bangalore|Pune|Hyderabad|Noida|Gurgaon|Delhi|Mumbai)[^)]*)\\)");
        Matcher parenMatcher = parenLoc.matcher(subject);
        if (parenMatcher.find()) {
            return parenMatcher.group(1).trim();
        }

        return null;
    }

    private String extractJobUrl(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        Pattern urlPattern = Pattern.compile("(?i)https?://[^\\s\\)\"']*(?:job|career|application|recooty\\.com|myworkdayjobs\\.com|greenhouse\\.io|lever\\.co|smartrecruiters\\.com|indeed\\.com/viewjob)[^\\s\\)\"']*");
        Matcher matcher = urlPattern.matcher(body);
        if (matcher.find()) {
            String url = matcher.group(0).trim().replaceAll("[\\.,;]$", "");
            if (url.length() < 500) {
                return url;
            }
        }
        return null;
    }

    private String buildNotes(String subject, String sender, String company, String role, String location, String jobUrl, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("Application identified for ").append(role).append(" at ").append(company);
        if (location != null && !location.isBlank()) {
            sb.append(" (").append(location).append(")");
        }
        sb.append(".\n");



        String senderEmail = cleanEmailAddress(sender);
        if (!senderEmail.isBlank()) {
            sb.append("• Source: ").append(senderEmail).append("\n");
        }

        sb.append("• Email Subject: ").append(subject);

        return sb.toString();
    }

    private double calculateConfidence(String subject, String sender, String lowerBody, String company, String role, String status, String jobUrl, String location) {
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
        if (lowerSubject.contains("interview invitation") || lowerSubject.contains("schedule your interview") || lowerSubject.contains("complete your ai interview")) {
            score += 0.30;
        }

        // Offer signals
        if (lowerSubject.contains("job offer") || lowerSubject.contains("offer of employment") || lowerSubject.contains("offer letter")) {
            score += 0.40;
        }

        // Status signals
        if ("REJECTED".equals(status)) {
            score += 0.25;
        } else if ("OFFER".equals(status)) {
            score += 0.30;
        } else if ("ACCEPTED".equals(status)) {
            score += 0.30;
        } else if ("TECHNICAL_ROUND".equals(status) || "HR_ROUND".equals(status) || "PHONE_SCREEN".equals(status)) {
            score += 0.25;
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
            score += 0.20;
        }

        // 3. Verified role bonus
        if (role != null && !role.equals("Applicant Position")) {
            score += 0.20;
        }

        // 4. Verified company bonus
        if (company != null && !company.equals("Unknown Company") && isValidCompanyName(company)) {
            score += 0.20;
        }

        // 5. Rich details bonus
        if (jobUrl != null || location != null) {
            score += 0.10;
        }

        // 6. Body context signals
        if (lowerBody.contains("thank you for applying")
                || lowerBody.contains("thanks for applying")
                || lowerBody.contains("application has been received")
                || lowerBody.contains("review candidate for job application")
                || lowerBody.contains("application was sent")
                || lowerBody.contains("items were sent to")
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
