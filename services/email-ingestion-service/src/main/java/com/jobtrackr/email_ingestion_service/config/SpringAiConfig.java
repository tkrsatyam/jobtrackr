package com.jobtrackr.email_ingestion_service.config;

import com.jobtrackr.email_ingestion_service.client.GeminiChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfig {

    @Value("${spring.ai.openai.api-key:${GEMINI_API_KEY:}}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://generativelanguage.googleapis.com/v1beta/openai}")
    private String baseUrl;

    @Value("${jobtrackr.ai.models:${GEMINI_MODELS:${spring.ai.openai.chat.options.model:${GEMINI_MODEL:gemini-3.1-flash-lite,gemini-3.5-flash-lite,gemini-2.5-flash-lite,gemini-3.8-flash}}}}")
    private String models;

    @Bean
    public ChatClient chatClient() {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        ChatModel chatModel = new GeminiChatModel(baseUrl, apiKey, models);
        return ChatClient.create(chatModel);
    }
}
