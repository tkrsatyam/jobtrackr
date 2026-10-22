package com.jobtrackr.email_ingestion_service.dto.gmail;

import java.util.List;

public record GmailPayload(
        String mimeType,
        List<GmailHeader> headers,
        GmailBody body,
        List<GmailPayload> parts
) {}
