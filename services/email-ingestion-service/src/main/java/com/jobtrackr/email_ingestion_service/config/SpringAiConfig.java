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

    @Value("${spring.ai.openai.chat.options.model:gemini-1.5-flash}")
    private String model;

    @Bean
    public ChatClient chatClient() {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        ChatModel chatModel = new GeminiChatModel(baseUrl, apiKey, model);
        return ChatClient.create(chatModel);
    }
}
