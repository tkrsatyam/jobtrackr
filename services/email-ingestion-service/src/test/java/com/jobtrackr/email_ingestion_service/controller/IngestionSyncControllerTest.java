package com.jobtrackr.email_ingestion_service.controller;

import com.jobtrackr.email_ingestion_service.service.GmailPollingWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IngestionSyncControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GmailPollingWorker gmailPollingWorker;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new IngestionSyncController(gmailPollingWorker)).build();
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
}
