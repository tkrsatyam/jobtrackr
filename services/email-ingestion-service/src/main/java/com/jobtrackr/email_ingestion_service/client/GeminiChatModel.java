package com.jobtrackr.email_ingestion_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class GeminiChatModel implements ChatModel {

    private final RestClient restClient;
    private final List<String> models;
    private final Map<String, Instant> cooldownUntil = new ConcurrentHashMap<>();

    public GeminiChatModel(String baseUrl, String apiKey, String model) {
        this(baseUrl, apiKey, parseModels(model));
    }

    public GeminiChatModel(String baseUrl, String apiKey, List<String> models) {
        this(buildRestClient(baseUrl, apiKey), models);
    }

    GeminiChatModel(RestClient restClient, List<String> models) {
        this.restClient = restClient;
        this.models = (models != null && !models.isEmpty()) ? models : List.of("gemini-3.1-flash-lite");
    }

    private static RestClient buildRestClient(String baseUrl, String apiKey) {
        String normalizedUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return RestClient.builder()
                .baseUrl(normalizedUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private static List<String> parseModels(String model) {
        if (model == null || model.isBlank()) {
            return List.of("gemini-3.1-flash-lite");
        }
        return Arrays.stream(model.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (prompt.getInstructions() != null && !prompt.getInstructions().isEmpty()) {
            for (Message msg : prompt.getInstructions()) {
                String role = switch (msg.getMessageType()) {
                    case SYSTEM -> "system";
                    case ASSISTANT -> "assistant";
                    default -> "user";
                };
                messages.add(Map.of("role", role, "content", msg.getText() != null ? msg.getText() : ""));
            }
        } else {
            messages.add(Map.of("role", "user", "content", prompt.getContents() != null ? prompt.getContents() : ""));
        }

        Instant now = Instant.now();
        List<String> candidateModels = new ArrayList<>();
        for (String m : models) {
            Instant cooldown = cooldownUntil.get(m);
            if (cooldown == null || now.isAfter(cooldown)) {
                candidateModels.add(m);
            } else {
                log.debug("Model '{}' is in cooldown until {}, skipping.", m, cooldown);
            }
        }

        // If all models are currently cooling down, fall back to the one expiring earliest
        if (candidateModels.isEmpty()) {
            log.warn("All configured AI models are in cooldown. Attempting model with earliest expiry.");
            String earliestModel = models.stream()
                    .min(Comparator.comparing(m -> cooldownUntil.getOrDefault(m, Instant.EPOCH)))
                    .orElse(models.get(0));
            candidateModels.add(earliestModel);
        }

        Exception lastException = null;

        for (String targetModel : candidateModels) {
            Map<String, Object> requestBody = Map.of(
                    "model", targetModel,
                    "messages", messages
            );

            int maxAttempts = 2;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    OpenAiChatCompletionResponse response = restClient.post()
                            .uri("/chat/completions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(requestBody)
                            .retrieve()
                            .body(OpenAiChatCompletionResponse.class);

                    if (response != null && response.choices() != null && !response.choices().isEmpty()) {
                        String rawContent = response.choices().get(0).message().content();
                        String cleaned = cleanJsonContent(rawContent);
                        log.info("AI extraction succeeded using model '{}'", targetModel);
                        return new ChatResponse(List.of(new Generation(new AssistantMessage(cleaned))));
                    }
                    break;
                } catch (Exception e) {
                    lastException = e;
                    boolean isRateLimit = isRateLimitError(e);
                    if (isRateLimit) {
                        long cooldownSeconds = extractRetryDelaySeconds(e, 300);
                        cooldownUntil.put(targetModel, Instant.now().plusSeconds(cooldownSeconds));
                        log.warn("Model '{}' hit rate limit / quota exhaustion (429). Placed in cooldown for {}s. Failing over to next model in chain...",
                                targetModel, cooldownSeconds);
                        break;
                    }

                    boolean isTransient = isTransientError(e);
                    if (isTransient && attempt < maxAttempts) {
                        long waitMs = attempt * 1500L;
                        log.warn("Transient error on model '{}' (attempt {}/{}): {}. Retrying in {}ms...",
                                targetModel, attempt, maxAttempts, e.getMessage(), waitMs);
                        try {
                            Thread.sleep(waitMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted during retry wait", ie);
                        }
                    } else {
                        log.warn("Model '{}' execution failed: {}. Moving to next model in fallback chain.", targetModel, e.getMessage());
                        break;
                    }
                }
            }
        }

        log.error("All AI models in fallback chain failed: {}. Last error: {}",
                models, lastException != null ? lastException.getMessage() : "unknown");
        throw new RuntimeException("All configured AI models failed in fallback chain: " + models, lastException);
    }

    private boolean isRateLimitError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("429") || msg.contains("resource_exhausted") || msg.contains("quota exceeded");
    }

    private boolean isTransientError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("503") || msg.contains("high demand")
                || msg.contains("temporarily unavailable")
                || msg.contains("timeout") || msg.contains("connection reset");
    }

    private long extractRetryDelaySeconds(Exception e, long defaultSeconds) {
        try {
            String msg = e.getMessage();
            if (msg != null && msg.contains("\"retryDelay\": \"")) {
                int start = msg.indexOf("\"retryDelay\": \"") + 15;
                int end = msg.indexOf("s\"", start);
                if (end != -1) {
                    String delayStr = msg.substring(start, end).trim();
                    return Long.parseLong(delayStr);
                }
            }
        } catch (Exception ignored) {}
        return defaultSeconds;
    }

    private String cleanJsonContent(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    public record OpenAiChatCompletionResponse(List<Choice> choices) {
        public record Choice(Message message) {}
        public record Message(String role, String content) {}
    }
}
