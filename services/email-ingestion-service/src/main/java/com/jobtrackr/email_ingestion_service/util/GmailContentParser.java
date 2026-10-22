package com.jobtrackr.email_ingestion_service.util;

import com.jobtrackr.email_ingestion_service.dto.gmail.GmailHeader;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailMessageDetail;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailPayload;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GmailContentParser {

    private static final Pattern ANCHOR_PATTERN = Pattern.compile("(?i)<a\\s+[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern BREAK_TAG_PATTERN = Pattern.compile("(?i)<(?:br|p|/p|div|/div|tr|/tr|li|/li)\\s*/?>");
    private static final Pattern STRIP_TAGS_PATTERN = Pattern.compile("<[^>]*>");

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
                if ("text/html".equalsIgnoreCase(payload.mimeType()) || decodedStr.contains("<html") || decodedStr.contains("<body") || decodedStr.contains("<div")) {
                    decodedStr = convertHtmlToText(decodedStr);
                }
                sb.append(decodedStr).append("\n");
            } catch (Exception ignored) {
            }
        }
        if (payload.parts() != null) {
            for (GmailPayload part : payload.parts()) {
                extractTextFromPayload(part, sb);
            }
        }
    }

    public static String convertHtmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        // Convert <a href="url">text</a> to text (url)
        Matcher matcher = ANCHOR_PATTERN.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String url = matcher.group(1).trim();
            String linkText = matcher.group(2).replaceAll("<[^>]*>", "").trim();
            String replacement;
            if (linkText.isBlank() || linkText.equalsIgnoreCase(url)) {
                replacement = url;
            } else {
                replacement = linkText + " (" + url + ")";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        String processed = sb.toString();

        // Convert breaks/paragraphs/rows to line breaks
        processed = BREAK_TAG_PATTERN.matcher(processed).replaceAll("\n");

        // Strip remaining HTML tags
        processed = STRIP_TAGS_PATTERN.matcher(processed).replaceAll(" ");

        // Decode basic HTML entities
        processed = processed.replace("&amp;", "&")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("(?m)^\\s+$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return processed;
    }
}
