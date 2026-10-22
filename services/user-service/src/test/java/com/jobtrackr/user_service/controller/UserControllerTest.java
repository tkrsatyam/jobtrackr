package com.jobtrackr.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrackr.user_service.dto.LinkGoogleRequest;
import com.jobtrackr.user_service.dto.UserProfileDTO;
import com.jobtrackr.user_service.exception.AccountAlreadyLinkedException;
import com.jobtrackr.user_service.exception.GlobalExceptionHandler;
import com.jobtrackr.user_service.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/users/me should return user profile with Google connection status")
    void shouldReturnUserProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        UserProfileDTO dto = UserProfileDTO.builder()
                .id(userId)
                .email("satyam@test.com")
                .fullName("Satyam Kumar")
                .role("USER")
                .provider("LOCAL")
                .googleConnected(true)
                .googleEmail("satyam@gmail.com")
                .build();

        when(userService.getProfile(userId)).thenReturn(dto);

        mockMvc.perform(get("/api/users/me")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(userId.toString())))
                .andExpect(jsonPath("$.googleConnected", is(true)))
                .andExpect(jsonPath("$.googleEmail", is("satyam@gmail.com")));
    }

    @Test
    @DisplayName("POST /api/users/me/link-google/init should set oauth2_link_user_id cookie and return authUrl")
    void shouldInitLinkGoogleAndSetCookie() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/users/me/link-google/init")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(UserController.OAUTH2_LINK_USER_ID_COOKIE))
                .andExpect(cookie().value(UserController.OAUTH2_LINK_USER_ID_COOKIE, userId.toString()))
                .andExpect(cookie().httpOnly(UserController.OAUTH2_LINK_USER_ID_COOKIE, true))
                .andExpect(jsonPath("$.authUrl", is("/api/auth/oauth2/authorize/google")));
    }

    @Test
    @DisplayName("POST /api/users/me/link-google should link Google account and return 200")
    void shouldLinkGoogleAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        LinkGoogleRequest request = LinkGoogleRequest.builder()
                .providerUserId("google-sub-456")
                .email("satyam@gmail.com")
                .accessToken("token")
                .build();

        UserProfileDTO dto = UserProfileDTO.builder()
                .id(userId)
                .email("satyam@test.com")
                .googleConnected(true)
                .googleEmail("satyam@gmail.com")
                .build();

        when(userService.linkGoogle(eq(userId), any(LinkGoogleRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/api/users/me/link-google")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleConnected", is(true)))
                .andExpect(jsonPath("$.googleEmail", is("satyam@gmail.com")));
    }

    @Test
    @DisplayName("POST /api/users/me/link-google should return 409 Conflict when AccountAlreadyLinkedException thrown")
    void shouldReturn409ConflictWhenAccountAlreadyLinked() throws Exception {
        UUID userId = UUID.randomUUID();
        LinkGoogleRequest request = LinkGoogleRequest.builder()
                .providerUserId("google-sub-duplicate")
                .email("satyam@gmail.com")
                .build();

        when(userService.linkGoogle(eq(userId), any(LinkGoogleRequest.class)))
                .thenThrow(new AccountAlreadyLinkedException("This Google account is already linked to another JobTrackr user"));

        mockMvc.perform(post("/api/users/me/link-google")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("already linked to another JobTrackr user")));
    }

    @Test
    @DisplayName("DELETE /api/users/me/unlink-google should unlink Google account and return 204")
    void shouldUnlinkGoogleAccount() throws Exception {
        UUID userId = UUID.randomUUID();

        doNothing().when(userService).unlinkGoogle(userId);

        mockMvc.perform(delete("/api/users/me/unlink-google")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        verify(userService).unlinkGoogle(userId);
    }
}
