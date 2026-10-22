package com.jobtrackr.email_ingestion_service.kafka.consumer;

import com.jobtrackr.email_ingestion_service.entity.EmailProcessingStatus;
import com.jobtrackr.email_ingestion_service.entity.ProcessedEmail;
import com.jobtrackr.email_ingestion_service.kafka.event.ApplicationReviewRejectedEvent;
import com.jobtrackr.email_ingestion_service.repository.ProcessedEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationReviewRejectedConsumer {

    private final ProcessedEmailRepository processedEmailRepository;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @KafkaListener(
            topics = "${jobtrackr.kafka.topics.application-review-rejected:application-review-rejected}",
            groupId = "${spring.kafka.consumer.group-id:email-ingestion-service-group}"
    )
    public void consume(ApplicationReviewRejectedEvent event) {
        if (event == null || event.userId() == null || event.emailMessageId() == null) {
            return;
        }

        log.info("Received ApplicationReviewRejectedEvent for user {} messageId {}", event.userId(), event.emailMessageId());

        // 1. Persist/update tombstone in PostgreSQL
        Optional<ProcessedEmail> processedOpt = processedEmailRepository.findByUserIdAndMessageId(event.userId(), event.emailMessageId());
        if (processedOpt.isPresent()) {
            ProcessedEmail processed = processedOpt.get();
            processed.setStatus(EmailProcessingStatus.REJECTED);
            processedEmailRepository.save(processed);
        } else {
            ProcessedEmail rejected = ProcessedEmail.builder()
                    .userId(event.userId())
                    .messageId(event.emailMessageId())
                    .status(EmailProcessingStatus.REJECTED)
                    .build();
            processedEmailRepository.save(rejected);
        }

        // 2. Add to Redis Set for O(1) lookups
        try {
            if (redisTemplate != null) {
                String redisKey = "jobtrackr:user:" + event.userId() + ":rejected_emails";
                redisTemplate.opsForSet().add(redisKey, event.emailMessageId());
                log.info("Cached rejected email {} in Redis set {}", event.emailMessageId(), redisKey);
            }
        } catch (Exception e) {
            log.warn("Failed to store rejected email in Redis: {}", e.getMessage());
        }
    }
}
