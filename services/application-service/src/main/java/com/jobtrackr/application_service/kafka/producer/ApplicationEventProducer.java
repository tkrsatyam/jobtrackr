package com.jobtrackr.application_service.kafka.producer;

import com.jobtrackr.application_service.kafka.event.ApplicationCreatedEvent;
import com.jobtrackr.application_service.kafka.event.ApplicationDeletedEvent;
import com.jobtrackr.application_service.kafka.event.StatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationEventProducer {

    private static final String TOPIC_CREATED = "application.created";
    private static final String TOPIC_STATUS_UPDATED = "application.status.updated";
    private static final String TOPIC_DELETED = "application.deleted";

//    Kafka events will be uncommented after setup for Kafka is done
//    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendApplicationCreated(ApplicationCreatedEvent event) {
//        kafkaTemplate.send(TOPIC_CREATED, event.getApplicationId().toString(), event);
        log.info("Application created event queued | applicationId={} company={}",
                event.getApplicationId(), event.getCompanyName());
    }

    public void sendStatusUpdated(StatusUpdatedEvent event) {
//        kafkaTemplate.send(TOPIC_STATUS_UPDATED, event.getApplicationId().toString(), event);
        log.info("Status updated event queued | applicationId={} transition={} -> {}",
                event.getApplicationId(), event.getPreviousStatus(), event.getNewStatus());
    }

    public void sendApplicationDeleted(ApplicationDeletedEvent event) {
//        kafkaTemplate.send(TOPIC_DELETED, event.getApplicationId().toString(), event);
        log.info("Application deleted event queued | applicationId={}", event.getApplicationId());
    }
}
