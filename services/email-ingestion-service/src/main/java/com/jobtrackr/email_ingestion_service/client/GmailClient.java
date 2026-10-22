package com.jobtrackr.email_ingestion_service.client;

import com.jobtrackr.email_ingestion_service.dto.gmail.GmailHistoryResponse;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailMessageDetail;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailMessageListResponse;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailMessageRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class GmailClient {

    private final RestClient restClient;

    public GmailClient() {
        this.restClient = RestClient.builder()
                .baseUrl("https://gmail.googleapis.com/gmail/v1/users/me")
                .build();
    }

    public GmailClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<GmailMessageRef> listMessages(String accessToken, String query, int maxResults) {
        try {
            GmailMessageListResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/messages")
                            .queryParam("q", query)
                            .queryParam("maxResults", maxResults)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GmailMessageListResponse.class);

            if (response != null && response.messages() != null) {
                return response.messages();
            }
        } catch (Exception e) {
            log.error("Failed to list Gmail messages: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    public Optional<GmailMessageDetail> getMessage(String accessToken, String messageId) {
        try {
            GmailMessageDetail message = restClient.get()
                    .uri("/messages/{id}?format=full", messageId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GmailMessageDetail.class);
            return Optional.ofNullable(message);
        } catch (Exception e) {
            log.error("Failed to get Gmail message {}: {}", messageId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<GmailHistoryResponse> listHistory(String accessToken, String startHistoryId) {
        try {
            GmailHistoryResponse history = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/history")
                            .queryParam("startHistoryId", startHistoryId)
                            .queryParam("historyTypes", "messageAdded")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GmailHistoryResponse.class);
            return Optional.ofNullable(history);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Gone e) {
            log.warn("History ID {} expired or not found: {}", startHistoryId, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error retrieving Gmail history with startHistoryId {}: {}", startHistoryId, e.getMessage());
            return Optional.empty();
        }
    }
}
