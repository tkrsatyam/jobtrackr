package com.jobtrackr.email_ingestion_service.controller;

import com.jobtrackr.email_ingestion_service.dto.IngestionStatusResponse;
import com.jobtrackr.email_ingestion_service.dto.ProcessedEmailResponse;
import com.jobtrackr.email_ingestion_service.entity.EmailProcessingStatus;
import com.jobtrackr.email_ingestion_service.service.EmailIngestionQueryService;
import com.jobtrackr.email_ingestion_service.service.GmailPollingWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IngestionSyncControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GmailPollingWorker gmailPollingWorker;

    @Mock
    private EmailIngestionQueryService queryService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new IngestionSyncController(gmailPollingWorker, queryService)).build();
    }

    @Test
    @DisplayName("Should trigger sync for all users when X-User-Id header is absent")
    void testSyncAll() throws Exception {
        when(gmailPollingWorker.pollAllUsers()).thenReturn(3);

        mockMvc.perform(post("/api/ingestion/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.discoveredCount").value(3));

        verify(gmailPollingWorker).pollAllUsers();
    }

    @Test
    @DisplayName("Should trigger sync for specific user when X-User-Id header is present")
    void testSyncSpecificUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(gmailPollingWorker.pollForUser(eq(userId))).thenReturn(1);

        mockMvc.perform(post("/api/ingestion/sync")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.discoveredCount").value(1));

        verify(gmailPollingWorker).pollForUser(eq(userId));
    }

    @Test
    @DisplayName("Should return ingestion status for user")
    void testGetStatus() throws Exception {
        UUID userId = UUID.randomUUID();
        IngestionStatusResponse response = new IngestionStatusResponse(
                true, "user@example.com", Instant.now(), 25, 10, 12, 2, 1
        );
        when(queryService.getStatus(userId)).thenReturn(response);

        mockMvc.perform(get("/api/ingestion/status")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.connectedEmail").value("user@example.com"))
                .andExpect(jsonPath("$.totalScanned").value(25))
                .andExpect(jsonPath("$.totalProcessed").value(10));
    }

    @Test
    @DisplayName("Should return paginated processed emails history")
    void testGetHistory() throws Exception {
        UUID userId = UUID.randomUUID();
        ProcessedEmailResponse emailResponse = new ProcessedEmailResponse(
                UUID.randomUUID(), "msg-123", "Job Offer", "hr@company.com",
                Instant.now(), "PROCESSED", BigDecimal.valueOf(0.98), null, Instant.now()
        );
        when(queryService.getHistory(eq(userId), eq(EmailProcessingStatus.PROCESSED), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(emailResponse), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/ingestion/history")
                        .header("X-User-Id", userId.toString())
                        .param("status", "PROCESSED")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].messageId").value("msg-123"))
                .andExpect(jsonPath("$.content[0].status").value("PROCESSED"));
    }

    @Test
    @DisplayName("Should trigger re-ingest for email message")
    void testReIngest() throws Exception {
        UUID userId = UUID.randomUUID();
        ProcessedEmailResponse emailResponse = new ProcessedEmailResponse(
                UUID.randomUUID(), "msg-456", "Interview invite", "recruiter@company.com",
                Instant.now(), "PROCESSED", BigDecimal.valueOf(0.95), null, Instant.now()
        );
        when(queryService.reIngestEmail(userId, "msg-456")).thenReturn(emailResponse);

        mockMvc.perform(post("/api/ingestion/emails/msg-456/re-ingest")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value("msg-456"))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }
}
