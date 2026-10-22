package com.jobtrackr.user_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkGoogleRequest {

    @NotBlank(message = "Google provider user ID is required")
    private String providerUserId;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    private String accessToken;

    private String refreshToken;

    private Long expiresInSeconds;

    private String scopes;
}
