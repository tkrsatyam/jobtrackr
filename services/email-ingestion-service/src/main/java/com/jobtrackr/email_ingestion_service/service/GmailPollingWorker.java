package com.jobtrackr.email_ingestion_service.service;

import com.jobtrackr.email_ingestion_service.client.GmailClient;
import com.jobtrackr.email_ingestion_service.client.UserServiceClient;
import com.jobtrackr.email_ingestion_service.dto.JobApplicationExtractionRecord;
import com.jobtrackr.email_ingestion_service.dto.UserGoogleAccountDTO;
import com.jobtrackr.email_ingestion_service.dto.UserGoogleTokenDTO;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailHistoryRecord;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailHistoryResponse;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailMessageDetail;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailMessageRef;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailPollingWorker {

    private static final String DEFAULT_SEARCH_QUERY =
            "subject:(application OR applied OR interview OR \"job offer\" OR \"offer letter\" OR \"offer of employment\" OR \"employment offer\" OR candidacy OR \"thank you for applying\")"
            + " -\"credit card\" -\"personal loan\" -\"loan\" -\"pre-approved\" -\"discount\" -\"cashback\" -\"weekly problem\" -\"digest\" -\"newsletter\" -\"contest\" -\"ipo\" -\"admission\" -\"admissions\" -\"mba\" -\"pgp\" -\"shacket\" -category:promotions -category:social";

    private final UserServiceClient userServiceClient;
    private final GmailClient gmailClient;
    private final JobApplicationExtractor jobApplicationExtractor;
    private final IngestionEventProducer ingestionEventProducer;
    private final EmailPollingStateRepository emailPollingStateRepository;
    private final ProcessedEmailRepository processedEmailRepository;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Value("${jobtrackr.ingestion.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    @Scheduled(fixedDelayString = "${jobtrackr.ingestion.poll-interval-ms:10800000}", initialDelay = 30000)
    public void scheduledPoll() {
        if (!schedulerEnabled) {
            log.debug("Scheduled email polling is disabled (jobtrackr.ingestion.scheduler.enabled=false)");
            return;
        }

        log.info("Starting scheduled email polling run (interval: 3h)");
        try {
            int discovered = pollAllUsers();
            log.info("Scheduled email polling run finished. Total applications discovered: {}", discovered);

            if (discovered > 0) {
                log.info("Discovered {} new applications during background poll", discovered);
                // [NOTIFICATION HOOK]: Trigger notification when notification-service is wired
                // notificationProducer.sendBackgroundSyncNotification(discovered);
            }
        } catch (Exception e) {
            log.error("Scheduled email polling run encountered an error: {}", e.getMessage(), e);
        }
    }

    public int pollAllUsers() {
        List<UserGoogleAccountDTO> accounts;
        try {
            accounts = userServiceClient.getGoogleAccounts();
        } catch (Exception e) {
            log.error("Failed to fetch Google accounts from user-service: {}", e.getMessage());
            return 0;
        }

        if (accounts == null || accounts.isEmpty()) {
            log.debug("No connected Google accounts found for polling");
            return 0;
        }

        int totalDiscovered = 0;
        for (UserGoogleAccountDTO account : accounts) {
            try {
                totalDiscovered += pollForUser(account.userId());
            } catch (Exception e) {
                log.error("Failed polling Gmail for user {}: {}", account.userId(), e.getMessage(), e);
            }
        }
        return totalDiscovered;
    }

    @Transactional
    public int pollForUser(UUID userId) {
        log.info("Polling emails for user {}", userId);

        UserGoogleTokenDTO tokenDTO;
        try {
            tokenDTO = userServiceClient.getGoogleToken(userId);
        } catch (Exception e) {
            log.error("Failed to retrieve access token for user {}: {}", userId, e.getMessage());
            return 0;
        }

        if (tokenDTO == null || tokenDTO.accessToken() == null || tokenDTO.accessToken().isBlank()) {
            log.warn("Empty access token received for user {}", userId);
            return 0;
        }

        String accessToken = tokenDTO.accessToken();
        EmailPollingState state = emailPollingStateRepository.findById(userId)
                .orElseGet(() -> EmailPollingState.builder().userId(userId).build());

        List<GmailMessageRef> messagesToProcess = new ArrayList<>();
        String latestHistoryId = state.getLastHistoryId();

        if (state.getLastHistoryId() != null && !state.getLastHistoryId().isBlank()) {
            Optional<GmailHistoryResponse> historyResponse = gmailClient.listHistory(accessToken, state.getLastHistoryId());
            if (historyResponse.isPresent() && historyResponse.get().history() != null) {
                for (GmailHistoryRecord record : historyResponse.get().history()) {
                    if (record.messagesAdded() != null) {
                        record.messagesAdded().stream()
                                .map(m -> m.message())
                                .filter(Objects::nonNull)
                                .forEach(messagesToProcess::add);
                    }
                }
                if (historyResponse.get().historyId() != null) {
                    latestHistoryId = maxHistoryId(latestHistoryId, historyResponse.get().historyId());
                }
            } else if (historyResponse.isEmpty()) {
                log.info("History ID {} invalidated or not found. Falling back to query search", state.getLastHistoryId());
                messagesToProcess = gmailClient.listMessages(accessToken, DEFAULT_SEARCH_QUERY, 25);
            }
        } else {
            messagesToProcess = gmailClient.listMessages(accessToken, DEFAULT_SEARCH_QUERY, 25);
        }

        int discoveredCount = 0;
        Set<String> processedInBatch = new HashSet<>();

        for (GmailMessageRef ref : messagesToProcess) {
            if (ref == null || ref.id() == null || !processedInBatch.add(ref.id())) {
                continue;
            }

            // Check if user previously rejected this message (Redis Set + PostgreSQL tombstone)
            if (isMessageRejected(userId, ref.id())) {
                log.info("Skipping user-rejected email message {}", ref.id());
                continue;
            }

            if (processedEmailRepository.existsByUserIdAndMessageId(userId, ref.id())) {
                continue;
            }

            Optional<GmailMessageDetail> detailOpt = gmailClient.getMessage(accessToken, ref.id());
            if (detailOpt.isEmpty()) {
                continue;
            }

            GmailMessageDetail detail = detailOpt.get();
            if (detail.historyId() != null) {
                latestHistoryId = maxHistoryId(latestHistoryId, detail.historyId());
            }

            String subject = GmailContentParser.getSubject(detail);
            String sender = GmailContentParser.getSender(detail);
            Instant receivedAt = GmailContentParser.getReceivedAt(detail);
            String bodyText = GmailContentParser.extractBodyText(detail);

            Optional<JobApplicationExtractionRecord> extraction = jobApplicationExtractor.extract(subject, sender, bodyText);

            if (extraction.isPresent()) {
                JobApplicationExtractionRecord rec = extraction.get();
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
                        ref.id(),
                        subject,
                        sender,
                        receivedAt
                );

                ingestionEventProducer.publishApplicationDiscovered(event);

                ProcessedEmail processed = ProcessedEmail.builder()
                        .userId(userId)
                        .messageId(ref.id())
                        .subject(subject)
                        .sender(sender)
                        .receivedAt(receivedAt)
                        .status(EmailProcessingStatus.PROCESSED)
                        .build();
                processedEmailRepository.save(processed);
                discoveredCount++;
            } else {
                ProcessedEmail skipped = ProcessedEmail.builder()
                        .userId(userId)
                        .messageId(ref.id())
                        .subject(subject)
                        .sender(sender)
                        .receivedAt(receivedAt)
                        .status(EmailProcessingStatus.SKIPPED)
                        .build();
                processedEmailRepository.save(skipped);
            }
        }

        state.setLastPolledAt(Instant.now());
        if (latestHistoryId != null) {
            state.setLastHistoryId(latestHistoryId);
        }
        emailPollingStateRepository.save(state);

        log.info("Finished polling for user {}. Applications discovered: {}", userId, discoveredCount);
        return discoveredCount;
    }

    private boolean isMessageRejected(UUID userId, String messageId) {
        String redisKey = "jobtrackr:user:" + userId + ":rejected_emails";
        try {
            if (redisTemplate != null) {
                Boolean isMember = redisTemplate.opsForSet().isMember(redisKey, messageId);
                if (Boolean.TRUE.equals(isMember)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Redis check failed for rejected email {}: {}", messageId, e.getMessage());
        }
        return processedEmailRepository.existsByUserIdAndMessageIdAndStatus(
                userId, messageId, EmailProcessingStatus.REJECTED
        );
    }

    private String maxHistoryId(String id1, String id2) {
        if (id1 == null) return id2;
        if (id2 == null) return id1;
        try {
            BigInteger b1 = new BigInteger(id1);
            BigInteger b2 = new BigInteger(id2);
            return b1.compareTo(b2) >= 0 ? id1 : id2;
        } catch (NumberFormatException e) {
            return id2;
        }
    }
}
