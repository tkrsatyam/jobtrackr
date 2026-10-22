package com.jobtrackr.email_ingestion_service.dto.gmail;

public record GmailMessageDetail(
        String id,
        String threadId,
        String historyId,
        String snippet,
        Long internalDate,
        GmailPayload payload
) {}
