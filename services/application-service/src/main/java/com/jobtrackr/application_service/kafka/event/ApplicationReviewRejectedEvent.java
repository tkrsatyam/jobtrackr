package com.jobtrackr.application_service.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record ApplicationReviewRejectedEvent(
        UUID userId,
        String emailMessageId,
        Instant rejectedAt
) {}
