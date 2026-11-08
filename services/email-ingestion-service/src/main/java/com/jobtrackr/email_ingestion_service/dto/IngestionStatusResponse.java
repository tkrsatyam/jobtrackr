package com.jobtrackr.email_ingestion_service.dto;

import java.time.Instant;

public record IngestionStatusResponse(
        boolean connected,
        String connectedEmail,
        Instant lastPolledAt,
        long totalScanned,
        long totalProcessed,
        long totalSkipped,
        long totalRejected,
        long totalFailed
) {}
