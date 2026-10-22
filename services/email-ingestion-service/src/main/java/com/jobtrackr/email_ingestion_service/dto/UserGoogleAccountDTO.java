package com.jobtrackr.email_ingestion_service.dto;

import java.util.UUID;

public record UserGoogleAccountDTO(
        UUID userId,
        String email,
        String providerUserId
) {}
