package com.jobtrackr.email_ingestion_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProcessedEmailResponse(
        UUID id,
        String messageId,
        String subject,
        String sender,
        Instant receivedAt,
        String status,
        BigDecimal confidenceScore,
        String rejectionReason,
        Instant processedAt
) {}
