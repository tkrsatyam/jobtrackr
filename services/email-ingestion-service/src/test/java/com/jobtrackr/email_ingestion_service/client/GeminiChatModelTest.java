package com.jobtrackr.email_ingestion_service.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiChatModelTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder().baseUrl("https://ai.example.com");
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    @Test
    @DisplayName("Should succeed on primary model without calling fallback")
    void testPrimaryModelSuccess() {
        GeminiChatModel model = new GeminiChatModel(restClientBuilder.build(), List.of("gemini-3.1-flash-lite", "gemini-3.5-flash-lite"));

        mockServer.expect(requestTo("https://ai.example.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("gemini-3.1-flash-lite")))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"companyName\\\":\\\"Acme\\\"}\"}}]}",
                        MediaType.APPLICATION_JSON
                ));

        ChatResponse response = model.call(new Prompt("Extract job"));
        assertThat(response.getResult().getOutput().getText()).isEqualTo("{\"companyName\":\"Acme\"}");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should fallback to secondary model when primary returns 429")
    void testFallbackOn429() {
        GeminiChatModel model = new GeminiChatModel(restClientBuilder.build(), List.of("gemini-3.1-flash-lite", "gemini-3.5-flash-lite"));

        // Primary model fails with 429
        mockServer.expect(requestTo("https://ai.example.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("gemini-3.1-flash-lite")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":429,\"message\":\"Quota exceeded for model: gemini-3.1-flash-lite\",\"details\":[{\"retryDelay\":\"60s\"}]}}"));

        // Secondary model succeeds
        mockServer.expect(requestTo("https://ai.example.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("gemini-3.5-flash-lite")))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"companyName\\\":\\\"Fallback Corp\\\"}\"}}]}",
                        MediaType.APPLICATION_JSON
                ));

        ChatResponse response = model.call(new Prompt("Extract job"));
        assertThat(response.getResult().getOutput().getText()).isEqualTo("{\"companyName\":\"Fallback Corp\"}");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw exception when all configured models fail")
    void testAllModelsFail() {
        GeminiChatModel model = new GeminiChatModel(restClientBuilder.build(), List.of("gemini-3.1-flash-lite", "gemini-3.5-flash-lite"));

        // Primary fails with 429
        mockServer.expect(requestTo("https://ai.example.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("gemini-3.1-flash-lite")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"error\":{\"code\":429}}"));

        // Secondary also fails with 429
        mockServer.expect(requestTo("https://ai.example.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("gemini-3.5-flash-lite")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"error\":{\"code\":429}}"));

        assertThatThrownBy(() -> model.call(new Prompt("Extract job")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("All configured AI models failed in fallback chain");

        mockServer.verify();
    }
}
