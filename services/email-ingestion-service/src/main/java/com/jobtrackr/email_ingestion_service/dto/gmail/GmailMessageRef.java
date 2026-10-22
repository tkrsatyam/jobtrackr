package com.jobtrackr.email_ingestion_service.dto.gmail;

public record GmailMessageRef(
        String id,
        String threadId
) {}
