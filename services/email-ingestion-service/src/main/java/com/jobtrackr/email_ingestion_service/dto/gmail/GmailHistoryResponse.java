package com.jobtrackr.email_ingestion_service.dto.gmail;

import java.util.List;

public record GmailHistoryResponse(
        List<GmailHistoryRecord> history,
        String historyId,
        String nextPageToken
) {}
