package com.jobtrackr.email_ingestion_service.dto.gmail;

import java.util.List;

public record GmailMessageListResponse(
        List<GmailMessageRef> messages,
        String nextPageToken,
        Long resultSizeEstimate
) {}
