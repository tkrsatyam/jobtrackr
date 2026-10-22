package com.jobtrackr.email_ingestion_service.dto;

import java.time.Instant;
import java.util.UUID;

public record UserGoogleTokenDTO(
        UUID userId,
        String email,
        String accessToken,
        Instant expiresAt
) {}
