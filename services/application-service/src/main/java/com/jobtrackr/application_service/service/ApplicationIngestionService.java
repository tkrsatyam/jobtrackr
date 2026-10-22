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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationIngestionService {

    private final ApplicationRepository applicationRepository;

    @Transactional
    public void processDiscoveredApplication(ApplicationDiscoveredEvent event) {
        if (event == null || event.userId() == null || event.companyName() == null) {
            log.warn("Discarding malformed ApplicationDiscoveredEvent: {}", event);
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

        // 2. Deduplication check within last 30 days
        LocalDate thirtyDaysAgo = appliedDate.minusDays(30);
        String roleTitle = event.roleTitle() != null ? event.roleTitle().trim() : "Software Engineer";
        List<Application> potentialDuplicates = applicationRepository.findPotentialDuplicates(
                event.userId(),
                event.companyName().trim(),
                roleTitle,
                thirtyDaysAgo
        );

        if (!potentialDuplicates.isEmpty()) {
            Application existing = potentialDuplicates.get(0);
            log.info("Found existing matching application {} for company '{}' and role '{}'. Appending update.",
                    existing.getApplicationId(), existing.getCompanyName(), existing.getRole());

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
                .source(ApplicationSource.EMAIL)
                .appliedDate(appliedDate)
                .notes(event.notes())
                .aiDiscovered(true)
                .requiresReview(true)
                .confidenceScore(confidence)
                .rawEmailSnippet(rawSnippet.trim())
                .emailMessageId(event.emailMessageId())
                .isArchived(false)
                .isDeleted(false)
                .build();

        applicationRepository.save(application);
        log.info("Persisted AI-discovered application {} for user {} awaiting review",
                application.getApplicationId(), event.userId());
    }
}
