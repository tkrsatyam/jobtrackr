package com.jobtrackr.application_service.kafka;

import com.jobtrackr.application_service.kafka.event.ApplicationDiscoveredEvent;
import com.jobtrackr.application_service.service.ApplicationIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class ApplicationDiscoveredConsumer {

    private final ApplicationIngestionService applicationIngestionService;

    @KafkaListener(
            topics = "${jobtrackr.kafka.topics.application-discovered:application-discovered}",
            groupId = "${spring.kafka.consumer.group-id:application-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ApplicationDiscoveredEvent event) {
        log.info("Received ApplicationDiscoveredEvent for user: {}, company: {}, role: {}",
                event.userId(), event.companyName(), event.roleTitle());
        try {
            applicationIngestionService.processDiscoveredApplication(event);
        } catch (Exception e) {
            log.error("Failed to process ApplicationDiscoveredEvent {}: {}", event.eventId(), e.getMessage(), e);
        }
    }
}
