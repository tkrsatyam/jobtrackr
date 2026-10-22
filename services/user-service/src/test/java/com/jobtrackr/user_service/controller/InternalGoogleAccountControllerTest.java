package com.jobtrackr.user_service.controller;

import com.jobtrackr.user_service.dto.internal.InternalGoogleAccountDTO;
import com.jobtrackr.user_service.dto.internal.InternalGoogleTokenResponse;
import com.jobtrackr.user_service.service.GoogleTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalGoogleAccountControllerTest {

    @Mock
    private GoogleTokenService googleTokenService;

    @InjectMocks
    private InternalGoogleAccountController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getGoogleAccounts_ReturnsList() throws Exception {
        UUID userId = UUID.randomUUID();
        when(googleTokenService.getLinkedGoogleAccounts())
                .thenReturn(List.of(new InternalGoogleAccountDTO(userId, "test@example.com", "sub-123")));

        mockMvc.perform(get("/api/users/internal/google-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$[0].email").value("test@example.com"));
    }

    @Test
    void getGoogleToken_Found_ReturnsToken() throws Exception {
        UUID userId = UUID.randomUUID();
        when(googleTokenService.getValidTokenForUser(userId))
                .thenReturn(Optional.of(new InternalGoogleTokenResponse(userId, "test@example.com", "ya29.sample", Instant.now().plusSeconds(3600))));

        mockMvc.perform(get("/api/users/internal/google-token/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.accessToken").value("ya29.sample"));
    }

    @Test
    void getGoogleToken_NotFound_Returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        when(googleTokenService.getValidTokenForUser(userId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/internal/google-token/" + userId))
                .andExpect(status().isNotFound());
    }
}
