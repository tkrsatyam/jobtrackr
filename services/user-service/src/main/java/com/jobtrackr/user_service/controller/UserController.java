package com.jobtrackr.user_service.controller;

import com.jobtrackr.user_service.dto.ChangePasswordRequest;
import com.jobtrackr.user_service.dto.UserProfileDTO;
import com.jobtrackr.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> getProfile(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileDTO> updateProfile(@RequestHeader("X-User-Id") Long userId,
                                                        @RequestBody UserProfileDTO dto) {
        return ResponseEntity.ok(userService.updateProfile(userId, dto));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@RequestHeader("X-User-Id") Long userId,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@RequestHeader("X-User-Id") Long userId) {
        userService.deleteAccount(userId);
        return ResponseEntity.noContent().build();
    }
}
