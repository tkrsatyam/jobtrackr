package com.jobtrackr.email_ingestion_service.service;

import com.jobtrackr.email_ingestion_service.client.GmailClient;
import com.jobtrackr.email_ingestion_service.client.UserServiceClient;
import com.jobtrackr.email_ingestion_service.dto.*;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailMessageDetail;
import com.jobtrackr.email_ingestion_service.entity.EmailPollingState;
import com.jobtrackr.email_ingestion_service.entity.EmailProcessingStatus;
import com.jobtrackr.email_ingestion_service.entity.ProcessedEmail;
import com.jobtrackr.email_ingestion_service.kafka.event.ApplicationDiscoveredEvent;
import com.jobtrackr.email_ingestion_service.kafka.producer.IngestionEventProducer;
import com.jobtrackr.email_ingestion_service.repository.EmailPollingStateRepository;
import com.jobtrackr.email_ingestion_service.repository.ProcessedEmailRepository;
import com.jobtrackr.email_ingestion_service.util.GmailContentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailIngestionQueryService {

    private final ProcessedEmailRepository processedEmailRepository;
    private final EmailPollingStateRepository emailPollingStateRepository;
    private final UserServiceClient userServiceClient;
    private final GmailClient gmailClient;
    private final JobApplicationExtractor jobApplicationExtractor;
    private final IngestionEventProducer ingestionEventProducer;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public IngestionStatusResponse getStatus(UUID userId) {
        String connectedEmail = null;
        boolean isConnected = false;
        try {
            UserGoogleTokenDTO tokenDTO = userServiceClient.getGoogleToken(userId);
            if (tokenDTO != null && tokenDTO.email() != null) {
                connectedEmail = tokenDTO.email();
                isConnected = true;
            }
        } catch (Exception e) {
            log.debug("User {} does not have an active Google token: {}", userId, e.getMessage());
        }

        Instant lastPolledAt = emailPollingStateRepository.findById(userId)
                .map(EmailPollingState::getLastPolledAt)
                .orElse(null);

        long totalScanned = processedEmailRepository.countByUserId(userId);
        long totalProcessed = processedEmailRepository.countByUserIdAndStatus(userId, EmailProcessingStatus.PROCESSED);
        long totalSkipped = processedEmailRepository.countByUserIdAndStatus(userId, EmailProcessingStatus.SKIPPED);
        long totalRejected = processedEmailRepository.countByUserIdAndStatus(userId, EmailProcessingStatus.REJECTED);
        long totalFailed = processedEmailRepository.countByUserIdAndStatus(userId, EmailProcessingStatus.FAILED);

        return new IngestionStatusResponse(
                isConnected,
                connectedEmail,
                lastPolledAt,
                totalScanned,
                totalProcessed,
                totalSkipped,
                totalRejected,
                totalFailed
        );
    }

    public Page<ProcessedEmailResponse> getHistory(UUID userId, EmailProcessingStatus status, Pageable pageable) {
        Page<ProcessedEmail> page;
        if (status != null) {
            page = processedEmailRepository.findByUserIdAndStatusOrderByReceivedAtDesc(userId, status, pageable);
        } else {
            page = processedEmailRepository.findByUserIdOrderByReceivedAtDesc(userId, pageable);
        }

        return page.map(e -> new ProcessedEmailResponse(
                e.getId(),
                e.getMessageId(),
                e.getSubject(),
                e.getSender(),
                e.getReceivedAt(),
                e.getStatus().name(),
                e.getConfidenceScore(),
                e.getRejectionReason(),
                e.getProcessedAt()
        ));
    }

    @Transactional
    public ProcessedEmailResponse reIngestEmail(UUID userId, String messageId) {
        log.info("Requesting re-ingestion of email {} for user {}", messageId, userId);

        ProcessedEmail email = processedEmailRepository.findByUserIdAndMessageId(userId, messageId)
                .orElseThrow(() -> new IllegalArgumentException("Processed email not found with messageId: " + messageId));

        // 1. Remove from Redis rejected set if present
        if (redisTemplate != null) {
            try {
                String redisKey = "jobtrackr:user:" + userId + ":rejected_emails";
                redisTemplate.opsForSet().remove(redisKey, messageId);
                log.info("Removed email {} from Redis rejected set {}", messageId, redisKey);
            } catch (Exception e) {
                log.warn("Failed to remove email {} from Redis set: {}", messageId, e.getMessage());
            }
        }

        // 2. Fetch fresh message details if bodyText is missing or fetch from Gmail
        String subject = email.getSubject();
        String sender = email.getSender();
        Instant receivedAt = email.getReceivedAt();
        String bodyText = email.getBodyText();

        if (bodyText == null || bodyText.isBlank()) {
            UserGoogleTokenDTO tokenDTO = userServiceClient.getGoogleToken(userId);
            if (tokenDTO == null || tokenDTO.accessToken() == null) {
                throw new IllegalStateException("Google account token unavailable for user " + userId);
            }

            Optional<GmailMessageDetail> detailOpt = gmailClient.getMessage(tokenDTO.accessToken(), messageId);
            if (detailOpt.isPresent()) {
                GmailMessageDetail detail = detailOpt.get();
                subject = GmailContentParser.getSubject(detail);
                sender = GmailContentParser.getSender(detail);
                receivedAt = GmailContentParser.getReceivedAt(detail);
                bodyText = GmailContentParser.extractBodyText(detail);
                email.setSubject(subject);
                email.setSender(sender);
                email.setReceivedAt(receivedAt);
                email.setBodyText(bodyText);
            }
        }

        // 3. AI Extraction
        Optional<JobApplicationExtractionRecord> extraction = jobApplicationExtractor.extract(subject, sender, bodyText);

        if (extraction.isPresent()) {
            JobApplicationExtractionRecord rec = extraction.get();
            BigDecimal confidence = rec.confidenceScore() != null ? BigDecimal.valueOf(rec.confidenceScore()) : null;
            email.setConfidenceScore(confidence);
            email.setRejectionReason(rec.rejectionReason());

            if (rec.isJobApplication() && rec.confidenceScore() != null && rec.confidenceScore() >= 0.60) {
                ApplicationDiscoveredEvent event = new ApplicationDiscoveredEvent(
                        UUID.randomUUID(),
                        userId,
                        rec.companyName(),
                        rec.roleTitle(),
                        rec.status(),
                        rec.location(),
                        rec.jobUrl(),
                        rec.recruiterName(),
                        rec.recruiterEmail(),
                        rec.notes(),
                        rec.confidenceScore(),
                        messageId,
                        subject,
                        sender,
                        receivedAt
                );

                ingestionEventProducer.publishApplicationDiscovered(event);

                email.setStatus(EmailProcessingStatus.PROCESSED);
                email.setLastError(null);
                processedEmailRepository.save(email);
                log.info("Re-ingested email {} as PROCESSED for user {}", messageId, userId);
            } else {
                email.setStatus(EmailProcessingStatus.SKIPPED);
                email.setLastError(null);
                if (email.getRejectionReason() == null) {
                    email.setRejectionReason("AI classified as non-job application or confidence below threshold");
                }
                processedEmailRepository.save(email);
                log.info("Re-evaluated email {} as SKIPPED for user {}", messageId, userId);
            }
        } else {
            email.setStatus(EmailProcessingStatus.SKIPPED);
            email.setLastError(null);
            email.setRejectionReason("Pre-filter: aggregated digest or newsletter sender");
            processedEmailRepository.save(email);
        }

        return new ProcessedEmailResponse(
                email.getId(),
                email.getMessageId(),
                email.getSubject(),
                email.getSender(),
                email.getReceivedAt(),
                email.getStatus().name(),
                email.getConfidenceScore(),
                email.getRejectionReason(),
                email.getProcessedAt()
        );
    }
}
