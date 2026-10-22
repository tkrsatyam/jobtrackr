package com.jobtrackr.email_ingestion_service.kafka.producer;

import com.jobtrackr.email_ingestion_service.kafka.event.ApplicationDiscoveredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${jobtrackr.kafka.topics.application-discovered:application-discovered}")
    private String topicName;

    public void publishApplicationDiscovered(ApplicationDiscoveredEvent event) {
        log.info("Publishing ApplicationDiscoveredEvent to topic {} for user {} company {}",
                topicName, event.userId(), event.companyName());
        kafkaTemplate.send(topicName, event.userId().toString(), event);
    }
}
