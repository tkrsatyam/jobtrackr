package com.jobtrackr.email_ingestion_service.util;

import com.jobtrackr.email_ingestion_service.dto.gmail.GmailHeader;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailMessageDetail;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailPayload;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

public class GmailContentParser {

    public static String getHeader(GmailMessageDetail detail, String headerName) {
        if (detail == null || detail.payload() == null || detail.payload().headers() == null) {
            return null;
        }
        return detail.payload().headers().stream()
                .filter(h -> h.name() != null && h.name().equalsIgnoreCase(headerName))
                .map(GmailHeader::value)
                .findFirst()
                .orElse(null);
    }

    public static String getSubject(GmailMessageDetail detail) {
        String subject = getHeader(detail, "Subject");
        return subject != null ? subject : "(No Subject)";
    }

    public static String getSender(GmailMessageDetail detail) {
        String sender = getHeader(detail, "From");
        return sender != null ? sender : "(Unknown Sender)";
    }

    public static Instant getReceivedAt(GmailMessageDetail detail) {
        if (detail == null) {
            return Instant.now();
        }
        if (detail.internalDate() != null) {
            return Instant.ofEpochMilli(detail.internalDate());
        }
        return Instant.now();
    }

    public static String extractBodyText(GmailMessageDetail detail) {
        if (detail == null || detail.payload() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        extractTextFromPayload(detail.payload(), builder);
        String text = builder.toString().trim();
        if (text.isEmpty() && detail.snippet() != null) {
            return detail.snippet();
        }
        return text;
    }

    private static void extractTextFromPayload(GmailPayload payload, StringBuilder sb) {
        if (payload == null) {
            return;
        }
        if (payload.body() != null && payload.body().data() != null && !payload.body().data().isBlank()) {
            try {
                byte[] decoded = Base64.getUrlDecoder().decode(payload.body().data());
                String decodedStr = new String(decoded, StandardCharsets.UTF_8);
                // Strip rudimentary HTML tags if mimeType is text/html
                if ("text/html".equalsIgnoreCase(payload.mimeType())) {
                    decodedStr = decodedStr.replaceAll("<[^>]*>", " ");
                }
                sb.append(decodedStr).append(" ");
            } catch (Exception ignored) {
            }
        }
        if (payload.parts() != null) {
            for (GmailPayload part : payload.parts()) {
                extractTextFromPayload(part, sb);
            }
        }
    }
}
