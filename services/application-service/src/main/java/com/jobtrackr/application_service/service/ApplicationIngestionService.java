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

            if (Boolean.TRUE.equals(existing.getRequiresReview())) {
                // If candidate is still awaiting review, compare confidence scores
                if (newConfidence != null && (existing.getConfidenceScore() == null || newConfidence.compareTo(existing.getConfidenceScore()) > 0)) {
                    log.info("Incoming application has higher confidence ({} > {}). Updating candidate details.",
                            newConfidence, existing.getConfidenceScore());
                    existing.setConfidenceScore(newConfidence);
                    if (event.roleTitle() != null && !event.roleTitle().isBlank() && !event.roleTitle().equals("Applicant Position")) {
                        existing.setRole(event.roleTitle().trim());
                    }
                    if (event.location() != null && !event.location().isBlank()) {
                        existing.setLocation(event.location().trim());
                    }
                    if (event.jobUrl() != null && !event.jobUrl().isBlank()) {
                        existing.setJobUrl(event.jobUrl().trim());
                    }
                    if (event.status() != null) {
                        try {
                            existing.setStatus(ApplicationStatus.valueOf(event.status()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                } else {
                    // Enrich missing fields from lower/equal confidence confirmation
                    if ((existing.getLocation() == null || existing.getLocation().isBlank()) && event.location() != null) {
                        existing.setLocation(event.location().trim());
                    }
                    if ((existing.getJobUrl() == null || existing.getJobUrl().isBlank()) && event.jobUrl() != null) {
                        existing.setJobUrl(event.jobUrl().trim());
                    }
                }
                if (event.emailReceivedAt() != null) {
                    existing.setEmailReceivedAt(event.emailReceivedAt());
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

                            // [NOTIFICATION HOOK]: Trigger notification when notification-service is wired
                            // notificationService.notifyStatusProposal(existing.getUserId(), existing.getApplicationId(), incomingStatus);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }

                if ((existing.getLocation() == null || existing.getLocation().isBlank()) && event.location() != null) {
                    existing.setLocation(event.location().trim());
                }
                if ((existing.getJobUrl() == null || existing.getJobUrl().isBlank()) && event.jobUrl() != null) {
                    existing.setJobUrl(event.jobUrl().trim());
                }
                if (existing.getEmailReceivedAt() == null && event.emailReceivedAt() != null) {
                    existing.setEmailReceivedAt(event.emailReceivedAt());
                }
            }

            String updateNote = "\n[Email update " + appliedDate + "]: " +
                    (event.emailSubject() != null ? event.emailSubject() + " - " : "") +
                    (event.notes() != null ? event.notes() : "");

            if (existing.getNotes() != null) {
                existing.setNotes(existing.getNotes() + updateNote);
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

        String rawSnippet = "Subject: " + (event.emailSubject() != null ? event.emailSubject() : "") +
                "\nFrom: " + (event.emailSender() != null ? event.emailSender() : "") +
                "\n" + (event.notes() != null ? event.notes() : "");

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
