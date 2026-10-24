package com.jobtrackr.application_service.kafka.producer;

import com.jobtrackr.application_service.kafka.event.ApplicationCreatedEvent;
import com.jobtrackr.application_service.kafka.event.ApplicationDeletedEvent;
import com.jobtrackr.application_service.kafka.event.ApplicationReviewRejectedEvent;
import com.jobtrackr.application_service.kafka.event.StatusUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class ApplicationEventProducer {

    private static final String TOPIC_CREATED = "application.created";
    private static final String TOPIC_STATUS_UPDATED = "application.status.updated";
    private static final String TOPIC_DELETED = "application.deleted";
    public static final String TOPIC_REVIEW_REJECTED = "application-review-rejected";

    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    public void sendApplicationCreated(ApplicationCreatedEvent event) {
        log.info("Application created event queued | applicationId={} company={}",
                event.getApplicationId(), event.getCompanyName());
    }

    public void sendStatusUpdated(StatusUpdatedEvent event) {
        log.info("Status updated event queued | applicationId={} transition={} -> {}",
                event.getApplicationId(), event.getPreviousStatus(), event.getNewStatus());
    }

    public void sendApplicationDeleted(ApplicationDeletedEvent event) {
        log.info("Application deleted event queued | applicationId={}", event.getApplicationId());
    }

    public void sendApplicationReviewRejected(UUID userId, String emailMessageId) {
        if (emailMessageId != null && !emailMessageId.isBlank()) {
            ApplicationReviewRejectedEvent event = new ApplicationReviewRejectedEvent(userId, emailMessageId, Instant.now());
            if (kafkaTemplate != null) {
                try {
                    kafkaTemplate.send(TOPIC_REVIEW_REJECTED, userId.toString(), event);
                } catch (Exception e) {
                    log.warn("Failed to publish ApplicationReviewRejectedEvent to Kafka: {}", e.getMessage());
                }
            }
            log.info("Application review rejected event published | userId={} messageId={}", userId, emailMessageId);
        }
    }
}
