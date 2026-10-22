package com.jobtrackr.user_service.dto.internal;

import java.time.Instant;
import java.util.UUID;

public record InternalGoogleTokenResponse(
        UUID userId,
        String email,
        String accessToken,
        Instant expiresAt
) {}
