package com.jobtrackr.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrackr.user_service.dto.AuthResponse;
import com.jobtrackr.user_service.dto.LoginRequest;
import com.jobtrackr.user_service.dto.RefreshTokenRequest;
import com.jobtrackr.user_service.dto.RegisterRequest;
import com.jobtrackr.user_service.exception.EmailAlreadyInUseException;
import com.jobtrackr.user_service.exception.InvalidCredentialsException;
import com.jobtrackr.user_service.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerWithValidRequestReturnsOkWithAuthResponse() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("jane@example.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setFullName("Jane Doe");

        AuthResponse response = AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .userId(UUID.randomUUID())
                .email("jane@example.com")
                .fullName("Jane Doe")
                .role("USER")
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void registerWithPasswordMismatchReturnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("jane@example.com");
        request.setPassword("password123");
        request.setConfirmPassword("does-not-match");
        request.setFullName("Jane Doe");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void registerWithDuplicateEmailReturnsConflict() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("jane@example.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setFullName("Jane Doe");

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyInUseException("Email already in use"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithValidCredentialsReturnsOk() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("password123");

        AuthResponse response = AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .email("jane@example.com")
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void loginWithInvalidCredentialsReturnsUnauthorized() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("wrong-password");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithValidTokenReturnsOk() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh-token");

        AuthResponse response = AuthResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .build();

        when(authService.refresh(any(RefreshTokenRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));
    }

    @Test
    void logoutWithBearerTokenReturnsNoContentAndCallsService() throws Exception {
        RefreshTokenRequest body = new RefreshTokenRequest();
        body.setRefreshToken("refresh-token");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        verify(authService).logout("access-token", "refresh-token");
    }

    @Test
    void logoutWithoutAuthorizationHeaderReturnsNoContentWithoutCallingService() throws Exception {
        RefreshTokenRequest body = new RefreshTokenRequest();
        body.setRefreshToken("refresh-token");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        verify(authService, never()).logout(any(), any());
    }
}