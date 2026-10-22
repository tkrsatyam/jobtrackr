package com.jobtrackr.email_ingestion_service.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfig {

    @Value("${spring.ai.openai.api-key:${GEMINI_API_KEY:}}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://generativelanguage.googleapis.com/v1beta/openai/}")
    private String baseUrl;

    @Bean
    public ChatClient chatClient() {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        OpenAiApi openAiApi = new OpenAiApi(baseUrl, apiKey);
        OpenAiChatModel chatModel = new OpenAiChatModel(
                openAiApi,
                OpenAiChatOptions.builder()
                        .model("gemini-1.5-flash")
                        .build()
        );
        return ChatClient.create(chatModel);
    }
}
