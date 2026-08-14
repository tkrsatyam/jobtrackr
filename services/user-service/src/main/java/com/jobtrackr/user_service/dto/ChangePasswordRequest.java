package com.jobtrackr.user_service.dto;

import com.jobtrackr.user_service.validation.PasswordMatches;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@PasswordMatches(
        field = "newPassword",
        confirmField = "confirmPassword",
        message = "New password and confirm password do not match"
)
public class ChangePasswordRequest {

    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;
    
    @NotBlank
    private String confirmPassword;
}
