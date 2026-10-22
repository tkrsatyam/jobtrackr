package com.jobtrackr.email_ingestion_service.dto;

public record IngestionSyncResponse(
        String status,
        int discoveredCount,
        String message
) {}
