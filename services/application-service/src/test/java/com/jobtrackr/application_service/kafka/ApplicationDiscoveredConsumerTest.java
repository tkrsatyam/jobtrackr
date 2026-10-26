package com.jobtrackr.application_service.kafka;

import com.jobtrackr.application_service.kafka.event.ApplicationDiscoveredEvent;
import com.jobtrackr.application_service.service.ApplicationIngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApplicationDiscoveredConsumerTest {

    @Mock
    private ApplicationIngestionService applicationIngestionService;

    @InjectMocks
    private ApplicationDiscoveredConsumer consumer;

    @Test
    void testConsumeDelegatesToIngestionService() {
        ApplicationDiscoveredEvent event = new ApplicationDiscoveredEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Google",
                "Software Engineer",
                "APPLIED",
                "Mountain View, CA",
                "https://careers.google.com",
                "Recruiter",
                "recruiter@google.com",
                "Applied via portal",
                0.95,
                "msg-123",
                "Application Received",
                "jobs@google.com",
                Instant.now()
        );

        consumer.consume(event);

        verify(applicationIngestionService).processDiscoveredApplication(event);
    }

    @Test
    void testConsumeHandlesExceptionGracefully() {
        ApplicationDiscoveredEvent event = new ApplicationDiscoveredEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Google",
                "Software Engineer",
                "APPLIED",
                null,
                null,
                null,
                null,
                null,
                null,
                "msg-123",
                "Subject",
                "sender@google.com",
                Instant.now()
        );

        doThrow(new RuntimeException("DB error")).when(applicationIngestionService).processDiscoveredApplication(event);

        // Should not throw or crash
        consumer.consume(event);

        verify(applicationIngestionService).processDiscoveredApplication(event);
    }
}
