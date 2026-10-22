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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class GeminiChatModel implements ChatModel {

    private final RestClient restClient;
    private final String model;

    public GeminiChatModel(String baseUrl, String apiKey, String model) {
        this.model = (model != null && !model.isBlank()) ? model : "gemini-3.5-flash";
        String normalizedUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restClient = RestClient.builder()
                .baseUrl(normalizedUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
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

        Map<String, Object> requestBody = Map.of(
                "model", this.model,
                "messages", messages
        );

        try {
            OpenAiChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(OpenAiChatCompletionResponse.class);

            if (response != null && response.choices() != null && !response.choices().isEmpty()) {
                String rawContent = response.choices().get(0).message().content();
                // Strip markdown code fences if Gemini wraps json in ```json ... ```
                String cleaned = cleanJsonContent(rawContent);
                return new ChatResponse(List.of(new Generation(new AssistantMessage(cleaned))));
            }
        } catch (Exception e) {
            log.error("Gemini ChatModel execution failed: {}", e.getMessage());
            throw new RuntimeException("Gemini API call failed", e);
        }

        return new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
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
