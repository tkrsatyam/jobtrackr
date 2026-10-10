package com.jobtrackr.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrackr.user_service.dto.ChangePasswordRequest;
import com.jobtrackr.user_service.dto.UserProfileDTO;
import com.jobtrackr.user_service.exception.InvalidCurrentPasswordException;
import com.jobtrackr.user_service.exception.UserNotFoundException;
import com.jobtrackr.user_service.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void getProfileReturnsProfile() throws Exception {
        UserProfileDTO profile = UserProfileDTO.builder()
                .id(userId)
                .email("jane@example.com")
                .fullName("Jane Doe")
                .role("USER")
                .provider("LOCAL")
                .build();

        when(userService.getProfile(userId)).thenReturn(profile);

        mockMvc.perform(get("/api/users/me")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void getProfileUserNotFoundReturns404() throws Exception {
        when(userService.getProfile(userId)).thenThrow(new UserNotFoundException(userId));

        mockMvc.perform(get("/api/users/me")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProfileReturnsUpdatedProfile() throws Exception {
        UserProfileDTO update = UserProfileDTO.builder().fullName("New Name").build();
        UserProfileDTO updated = UserProfileDTO.builder().id(userId).fullName("New Name").build();

        when(userService.updateProfile(eq(userId), any(UserProfileDTO.class))).thenReturn(updated);

        mockMvc.perform(put("/api/users/me")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"));
    }

    @Test
    void changePasswordWithCorrectOldPasswordReturnsNoContent() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old-password");
        request.setNewPassword("new-password123");
        request.setConfirmPassword("new-password123");

        mockMvc.perform(put("/api/users/me/password")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(userService).changePassword(eq(userId), any(ChangePasswordRequest.class));
    }

    @Test
    void changePasswordWithWrongOldPasswordReturnsBadRequest() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrong-password");
        request.setNewPassword("new-password123");
        request.setConfirmPassword("new-password123");

        doThrow(new InvalidCurrentPasswordException("Current password is incorrect"))
                .when(userService).changePassword(eq(userId), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/users/me/password")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordWithMismatchedConfirmationReturnsBadRequestWithoutCallingService() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old-password");
        request.setNewPassword("new-password123");
        request.setConfirmPassword("does-not-match");

        mockMvc.perform(put("/api/users/me/password")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any());
    }

    @Test
    void deleteAccountReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        verify(userService).deleteAccount(userId);
    }
}