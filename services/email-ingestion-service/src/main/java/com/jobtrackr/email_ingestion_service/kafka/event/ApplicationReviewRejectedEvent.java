package com.jobtrackr.email_ingestion_service.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record ApplicationReviewRejectedEvent(
        UUID userId,
        String emailMessageId,
        Instant rejectedAt
) {}
