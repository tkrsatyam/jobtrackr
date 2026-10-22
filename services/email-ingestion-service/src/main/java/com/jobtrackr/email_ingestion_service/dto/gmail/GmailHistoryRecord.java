package com.jobtrackr.email_ingestion_service.dto.gmail;

import java.util.List;

public record GmailHistoryRecord(
        String id,
        List<GmailMessageRefWrapper> messagesAdded
) {}
