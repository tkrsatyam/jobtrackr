package com.jobtrackr.application_service.service;

import com.jobtrackr.application_service.entity.Application;
import com.jobtrackr.application_service.entity.enums.ApplicationSource;
import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import com.jobtrackr.application_service.entity.enums.PriorityLevel;
import com.jobtrackr.application_service.kafka.event.ApplicationDiscoveredEvent;
import com.jobtrackr.application_service.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationIngestionService {

    private final ApplicationRepository applicationRepository;

    @Transactional
    public void processDiscoveredApplication(ApplicationDiscoveredEvent event) {
        if (event == null || event.userId() == null || event.companyName() == null || event.companyName().isBlank()) {
            log.warn("Invalid ApplicationDiscoveredEvent received, skipping: {}", event);
            return;
        }

        // 1. Idempotency check on emailMessageId
        if (event.emailMessageId() != null &&
                applicationRepository.existsByUserIdAndEmailMessageId(event.userId(), event.emailMessageId())) {
            log.info("Email message {} already ingested for user {}, skipping", event.emailMessageId(), event.userId());
            return;
        }

        LocalDate appliedDate = event.emailReceivedAt() != null
                ? LocalDate.ofInstant(event.emailReceivedAt(), ZoneId.systemDefault())
                : LocalDate.now();

        // 2. Matching and Deduplication
        LocalDate fourteenDaysAgo = appliedDate.minusDays(14);
        LocalDate ninetyDaysAgo = appliedDate.minusDays(90);
        String roleTitle = event.roleTitle() != null ? event.roleTitle().trim() : "Applicant Position";

        List<Application> potentialMatches = applicationRepository.findPotentialDuplicates(
                event.userId(),
                event.companyName().trim(),
                roleTitle,
                ninetyDaysAgo
        );

        Application existing = null;
        for (Application app : potentialMatches) {
            if (Boolean.TRUE.equals(app.getRequiresReview())) {
                if (app.getAppliedDate() != null && !app.getAppliedDate().isBefore(fourteenDaysAgo)) {
                    existing = app;
                    break;
                }
            } else {
                existing = app;
                break;
            }
        }

        // Single active application fallback for generic role titles
        boolean isGenericRole = roleTitle.equalsIgnoreCase("Applicant Position") || roleTitle.isBlank();
        if (existing == null && isGenericRole) {
            List<Application> activeCompanyApps = applicationRepository.findActiveByCompany(
                    event.userId(),
                    event.companyName().trim(),
                    ninetyDaysAgo
            );
            if (activeCompanyApps.size() == 1) {
                existing = activeCompanyApps.get(0);
                log.info("Matched single active application {} for company '{}' despite generic role title '{}'",
                        existing.getApplicationId(), existing.getCompanyName(), roleTitle);
            } else if (activeCompanyApps.size() > 1) {
                log.info("Found {} active applications for company '{}' with generic role title '{}'. Disambiguation needed; creating candidate awaiting review.",
                        activeCompanyApps.size(), event.companyName(), roleTitle);
            }
        }

        if (existing != null) {
            log.info("Found existing matching application {} for company '{}' and role '{}'.",
                    existing.getApplicationId(), existing.getCompanyName(), existing.getRole());

            BigDecimal newConfidence = event.confidenceScore() != null
                    ? BigDecimal.valueOf(event.confidenceScore()).setScale(2, RoundingMode.HALF_UP)
                    : null;

            ApplicationSource incomingSource = detectSource(event.emailSender(), event.jobUrl());

            // 1. Source upgrade: specific job boards supersede generic EMAIL or null
            if (isHigherPrioritySource(incomingSource, existing.getSource())) {
                log.info("Upgraded source from {} to {} for application {}",
                        existing.getSource(), incomingSource, existing.getApplicationId());
                existing.setSource(incomingSource);
            }

            // 2. Applied date: preserve earliest application date
            if (appliedDate != null && (existing.getAppliedDate() == null || appliedDate.isBefore(existing.getAppliedDate()))) {
                existing.setAppliedDate(appliedDate);
            }

            // 3. Multi-email snippet merge in chronological (ascending) order
            String mergedSnippets = mergeRawEmailSnippets(
                    existing.getRawEmailSnippet(),
                    existing.getEmailReceivedAt(),
                    event.emailReceivedAt(),
                    event.emailSubject(),
                    event.emailSender(),
                    event.notes()
            );
            existing.setRawEmailSnippet(mergedSnippets);

            // Update latest email tracking timestamp if newer
            if (event.emailReceivedAt() != null &&
                    (existing.getEmailReceivedAt() == null || event.emailReceivedAt().isAfter(existing.getEmailReceivedAt()))) {
                existing.setEmailReceivedAt(event.emailReceivedAt());
                if (event.emailMessageId() != null) {
                    existing.setEmailMessageId(event.emailMessageId());
                }
            }

            if (Boolean.TRUE.equals(existing.getRequiresReview())) {
                // If candidate is still awaiting review, update role/confidence if incoming is more confident
                if (newConfidence != null && (existing.getConfidenceScore() == null || newConfidence.compareTo(existing.getConfidenceScore()) > 0)) {
                    log.info("Incoming application has higher confidence ({} > {}). Updating candidate details.",
                            newConfidence, existing.getConfidenceScore());
                    existing.setConfidenceScore(newConfidence);
                    if (event.roleTitle() != null && !event.roleTitle().isBlank() && !event.roleTitle().equals("Applicant Position")) {
                        existing.setRole(event.roleTitle().trim());
                    }
                }

                // Field enrichment: populate missing fields
                if ((existing.getLocation() == null || existing.getLocation().isBlank()) && event.location() != null) {
                    existing.setLocation(event.location().trim());
                }
                if ((existing.getJobUrl() == null || existing.getJobUrl().isBlank()) && event.jobUrl() != null) {
                    existing.setJobUrl(event.jobUrl().trim());
                }

                // Advance status along pipeline if incoming status is more advanced
                if (event.status() != null) {
                    try {
                        ApplicationStatus incomingStatus = ApplicationStatus.valueOf(event.status());
                        if (shouldAdvanceStatus(existing.getStatus(), incomingStatus)) {
                            log.info("Advancing pending application status from {} to {} based on email {}",
                                    existing.getStatus(), incomingStatus, event.emailMessageId());
                            existing.setStatus(incomingStatus);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            } else {
                // Active application already confirmed by the user (Option B: Status Proposal)
                if (event.status() != null) {
                    try {
                        ApplicationStatus incomingStatus = ApplicationStatus.valueOf(event.status());
                        if (incomingStatus != existing.getStatus()) {
                            existing.setProposedStatus(incomingStatus);
                            String proposalReason = event.notes() != null && !event.notes().isBlank()
                                    ? event.notes()
                                    : (event.emailSubject() != null ? event.emailSubject() : "Detected from incoming email");
                            existing.setStatusProposalReason(proposalReason);
                            existing.setStatusProposalReceivedAt(event.emailReceivedAt() != null ? event.emailReceivedAt() : Instant.now());

                            log.info("Registered status proposal {} for active application {} based on email {}",
                                    incomingStatus, existing.getApplicationId(), event.emailMessageId());
                        }
                    } catch (IllegalArgumentException ignored) {}
                }

                if ((existing.getLocation() == null || existing.getLocation().isBlank()) && event.location() != null) {
                    existing.setLocation(event.location().trim());
                }
                if ((existing.getJobUrl() == null || existing.getJobUrl().isBlank()) && event.jobUrl() != null) {
                    existing.setJobUrl(event.jobUrl().trim());
                }
            }

            // Append update to notes if not already present
            String updateNote = "[Email update " + appliedDate + "]: " +
                    (event.emailSubject() != null ? event.emailSubject() + " - " : "") +
                    (event.notes() != null ? event.notes() : "");

            if (existing.getNotes() != null && !existing.getNotes().isBlank()) {
                if (event.notes() != null && !event.notes().isBlank() && !existing.getNotes().contains(event.notes().trim())) {
                    existing.setNotes(existing.getNotes() + "\n" + updateNote);
                }
            } else {
                existing.setNotes(updateNote.trim());
            }

            applicationRepository.save(existing);
            return;
        }

        // 3. Create candidate application with requiresReview = true
        ApplicationStatus status = ApplicationStatus.APPLIED;
        if (event.status() != null) {
            try {
                status = ApplicationStatus.valueOf(event.status());
            } catch (IllegalArgumentException ignored) {}
        }

        BigDecimal confidence = null;
        if (event.confidenceScore() != null) {
            confidence = BigDecimal.valueOf(event.confidenceScore()).setScale(2, RoundingMode.HALF_UP);
        }

        String rawSnippet = formatEmailSnippetBlock(
                event.emailReceivedAt(),
                event.emailSubject(),
                event.emailSender(),
                event.notes()
        );

        Application application = Application.builder()
                .userId(event.userId())
                .companyName(event.companyName().trim())
                .role(roleTitle)
                .status(status)
                .priority(PriorityLevel.MEDIUM)
                .location(event.location())
                .jobUrl(event.jobUrl())
                .source(detectSource(event.emailSender(), event.jobUrl()))
                .appliedDate(appliedDate)
                .notes(event.notes())
                .aiDiscovered(true)
                .requiresReview(true)
                .confidenceScore(confidence)
                .rawEmailSnippet(rawSnippet.trim())
                .emailMessageId(event.emailMessageId())
                .emailReceivedAt(event.emailReceivedAt())
                .isArchived(false)
                .isDeleted(false)
                .build();

        applicationRepository.save(application);
        log.info("Persisted AI-discovered application {} for user {} awaiting review",
                application.getApplicationId(), event.userId());
    }

    private boolean isHigherPrioritySource(ApplicationSource incoming, ApplicationSource current) {
        return getSourcePriority(incoming) > getSourcePriority(current);
    }

    private int getSourcePriority(ApplicationSource source) {
        if (source == null) return 0;
        return switch (source) {
            case LINKEDIN, NAUKRI, INDEED, INTERNSHALA, INSTAHYRE, ANGEL_LIST -> 3;
            case COMPANY_WEBSITE, REFERRAL -> 2;
            case EMAIL -> 1;
            case OTHER -> 0;
        };
    }

    private boolean shouldAdvanceStatus(ApplicationStatus current, ApplicationStatus incoming) {
        if (incoming == null || current == incoming) return false;
        if (current == ApplicationStatus.SAVED || current == ApplicationStatus.APPLIED) {
            return incoming != ApplicationStatus.SAVED;
        }
        return incoming == ApplicationStatus.OFFER || incoming == ApplicationStatus.REJECTED || incoming == ApplicationStatus.ACCEPTED;
    }

    private String formatEmailSnippetBlock(Instant receivedAt, String subject, String sender, String notes) {
        String timeStr = receivedAt != null ? DateTimeFormatter.ISO_INSTANT.format(receivedAt) : "Unknown Date";
        StringBuilder sb = new StringBuilder();
        sb.append("=== [").append(timeStr).append("] ");
        sb.append(subject != null && !subject.isBlank() ? subject : "No Subject");
        if (sender != null && !sender.isBlank()) {
            sb.append(" (").append(sender).append(")");
        }
        sb.append(" ===\n");
        if (notes != null && !notes.isBlank()) {
            sb.append(notes.trim());
        }
        return sb.toString();
    }

    private record EmailSnippetEntry(Instant timestamp, String blockText) {}

    private String mergeRawEmailSnippets(String existingSnippet, Instant existingReceivedAt,
                                         Instant incomingReceivedAt, String incomingSubject,
                                         String incomingSender, String incomingNotes) {
        String incomingBlock = formatEmailSnippetBlock(incomingReceivedAt, incomingSubject, incomingSender, incomingNotes);
        if (existingSnippet == null || existingSnippet.isBlank()) {
            return incomingBlock;
        }

        List<EmailSnippetEntry> entries = new ArrayList<>();
        if (existingSnippet.contains("=== [")) {
            String[] parts = existingSnippet.split("(?m)^(?==== \\[)");
            for (String part : parts) {
                String trimmedPart = part.trim();
                if (trimmedPart.isEmpty()) continue;
                Instant ts = extractTimestampFromBlock(trimmedPart, existingReceivedAt);
                entries.add(new EmailSnippetEntry(ts, trimmedPart));
            }
        } else {
            Instant ts = existingReceivedAt != null ? existingReceivedAt : Instant.EPOCH;
            entries.add(new EmailSnippetEntry(ts, existingSnippet.trim()));
        }

        Instant incomingTs = incomingReceivedAt != null ? incomingReceivedAt : Instant.now();
        entries.add(new EmailSnippetEntry(incomingTs, incomingBlock.trim()));
        entries.sort(Comparator.comparing(EmailSnippetEntry::timestamp));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append("\n\n");
            sb.append(entries.get(i).blockText());
        }
        return sb.toString();
    }

    private Instant extractTimestampFromBlock(String block, Instant fallback) {
        try {
            int start = block.indexOf("=== [");
            if (start != -1) {
                int end = block.indexOf("]", start + 5);
                if (end != -1) {
                    String iso = block.substring(start + 5, end).trim();
                    return Instant.parse(iso);
                }
            }
        } catch (Exception ignored) {}
        return fallback != null ? fallback : Instant.EPOCH;
    }

    private ApplicationSource detectSource(String sender, String jobUrl) {
        String lowerSender = sender != null ? sender.toLowerCase() : "";
        String lowerUrl = jobUrl != null ? jobUrl.toLowerCase() : "";

        if (lowerSender.contains("linkedin.com") || lowerUrl.contains("linkedin.com")) {
            return ApplicationSource.LINKEDIN;
        }
        if (lowerSender.contains("naukri.com") || lowerUrl.contains("naukri.com")) {
            return ApplicationSource.NAUKRI;
        }
        if (lowerSender.contains("indeed.com") || lowerUrl.contains("indeed.com")) {
            return ApplicationSource.INDEED;
        }
        if (lowerSender.contains("internshala.com") || lowerUrl.contains("internshala.com")) {
            return ApplicationSource.INTERNSHALA;
        }
        if (lowerSender.contains("instahyre.com") || lowerUrl.contains("instahyre.com")) {
            return ApplicationSource.INSTAHYRE;
        }
        return ApplicationSource.EMAIL;
    }
}
