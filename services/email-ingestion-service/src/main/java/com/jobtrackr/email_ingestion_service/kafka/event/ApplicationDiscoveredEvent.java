package com.jobtrackr.email_ingestion_service.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record ApplicationDiscoveredEvent(
        UUID eventId,
        UUID userId,
        String companyName,
        String roleTitle,
        String status,
        String location,
        String jobUrl,
        String recruiterName,
        String recruiterEmail,
        String notes,
        Double confidenceScore,
        String emailMessageId,
        String emailSubject,
        String emailSender,
        Instant emailReceivedAt
) {}
