package com.jobtrackr.user_service.dto.internal;

import java.util.UUID;

public record InternalGoogleAccountDTO(
        UUID userId,
        String email,
        String providerUserId
) {}
