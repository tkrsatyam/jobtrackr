package com.jobtrackr.user_service.controller;

import com.jobtrackr.user_service.dto.internal.InternalGoogleAccountDTO;
import com.jobtrackr.user_service.dto.internal.InternalGoogleTokenResponse;
import com.jobtrackr.user_service.service.GoogleTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/internal")
@RequiredArgsConstructor
public class InternalGoogleAccountController {

    private final GoogleTokenService googleTokenService;

    @GetMapping("/google-accounts")
    public ResponseEntity<List<InternalGoogleAccountDTO>> getGoogleAccounts() {
        return ResponseEntity.ok(googleTokenService.getLinkedGoogleAccounts());
    }

    @GetMapping("/google-token/{userId}")
    public ResponseEntity<InternalGoogleTokenResponse> getGoogleToken(@PathVariable("userId") UUID userId) {
        return googleTokenService.getValidTokenForUser(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
