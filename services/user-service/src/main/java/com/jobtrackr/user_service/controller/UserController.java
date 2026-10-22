package com.jobtrackr.user_service.controller;

import com.jobtrackr.user_service.dto.ChangePasswordRequest;
import com.jobtrackr.user_service.dto.LinkGoogleRequest;
import com.jobtrackr.user_service.dto.UserProfileDTO;
import com.jobtrackr.user_service.service.JwtService;
import com.jobtrackr.user_service.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    public static final String OAUTH2_LINK_USER_ID_COOKIE = "oauth2_link_user_id";

    private final UserService userService;
    private final JwtService jwtService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> getProfile(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileDTO> updateProfile(@RequestHeader("X-User-Id") UUID userId,
                                                        @RequestBody UserProfileDTO dto) {
        return ResponseEntity.ok(userService.updateProfile(userId, dto));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@RequestHeader("X-User-Id") UUID userId,
                                                @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/link-google/init")
    public ResponseEntity<Map<String, String>> initLinkGoogle(@RequestHeader("X-User-Id") UUID userId,
                                                              HttpServletResponse response) {
        String linkToken = jwtService.generateLinkToken(userId);
        Cookie cookie = new Cookie(OAUTH2_LINK_USER_ID_COOKIE, userId.toString());
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(300);
        response.addCookie(cookie);
        return ResponseEntity.ok(Map.of("authUrl", "/api/auth/oauth2/authorize/google?linkToken=" + linkToken));
    }

    @PostMapping("/me/link-google")
    public ResponseEntity<UserProfileDTO> linkGoogle(@RequestHeader("X-User-Id") UUID userId,
                                                     @Valid @RequestBody LinkGoogleRequest request) {
        return ResponseEntity.ok(userService.linkGoogle(userId, request));
    }

    @DeleteMapping("/me/unlink-google")
    public ResponseEntity<Void> unlinkGoogle(@RequestHeader("X-User-Id") UUID userId) {
        userService.unlinkGoogle(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@RequestHeader("X-User-Id") UUID userId) {
        userService.deleteAccount(userId);
        return ResponseEntity.noContent().build();
    }
}
