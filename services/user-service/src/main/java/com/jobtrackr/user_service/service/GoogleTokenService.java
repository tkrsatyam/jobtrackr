package com.jobtrackr.user_service.service;

import com.jobtrackr.user_service.dto.internal.InternalGoogleAccountDTO;
import com.jobtrackr.user_service.dto.internal.InternalGoogleTokenResponse;
import com.jobtrackr.user_service.entity.OAuthCredential;
import com.jobtrackr.user_service.entity.User;
import com.jobtrackr.user_service.repository.OAuthCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleTokenService {

    private final OAuthCredentialRepository oAuthCredentialRepository;
    private final AesGcmEncryptionService encryptionService;

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret:}")
    private String googleClientSecret;

    private final RestClient restClient = RestClient.builder().build();

    @Transactional(readOnly = true)
    public List<InternalGoogleAccountDTO> getLinkedGoogleAccounts() {
        return oAuthCredentialRepository.findAllByProvider(User.AuthProvider.GOOGLE).stream()
                .filter(cred -> cred.getUser() != null)
                .map(cred -> new InternalGoogleAccountDTO(
                        cred.getUser().getId(),
                        cred.getEmail(),
                        cred.getProviderUserId()
                ))
                .toList();
    }

    @Transactional
    public Optional<InternalGoogleTokenResponse> getValidTokenForUser(UUID userId) {
        Optional<OAuthCredential> credOpt = oAuthCredentialRepository.findByUserIdAndProvider(userId, User.AuthProvider.GOOGLE);
        if (credOpt.isEmpty()) {
            return Optional.empty();
        }

        OAuthCredential credential = credOpt.get();
        // If current access token is valid for at least another 2 minutes, return it directly
        if (credential.getAccessToken() != null
                && credential.getExpiresAt() != null
                && credential.getExpiresAt().isAfter(Instant.now().plusSeconds(120))) {
            return Optional.of(new InternalGoogleTokenResponse(
                    userId,
                    credential.getEmail(),
                    credential.getAccessToken(),
                    credential.getExpiresAt()
            ));
        }

        // Token is missing or near expiration; attempt refresh using refresh_token
        if (credential.getRefreshToken() == null) {
            log.warn("No refresh token stored for Google user {}", userId);
            if (credential.getAccessToken() != null) {
                return Optional.of(new InternalGoogleTokenResponse(
                        userId,
                        credential.getEmail(),
                        credential.getAccessToken(),
                        credential.getExpiresAt()
                ));
            }
            return Optional.empty();
        }

        try {
            String decryptedRefreshToken = encryptionService.decrypt(credential.getRefreshToken());
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("client_id", googleClientId);
            formData.add("client_secret", googleClientSecret);
            formData.add("refresh_token", decryptedRefreshToken);
            formData.add("grant_type", "refresh_token");

            Map<String, Object> response = restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.containsKey("access_token")) {
                String newAccessToken = (String) response.get("access_token");
                Number expiresIn = (Number) response.getOrDefault("expires_in", 3600);
                Instant newExpiresAt = Instant.now().plusSeconds(expiresIn.longValue());

                credential.setAccessToken(newAccessToken);
                credential.setExpiresAt(newExpiresAt);
                oAuthCredentialRepository.save(credential);

                log.info("Successfully refreshed Google access token for user {}", userId);
                return Optional.of(new InternalGoogleTokenResponse(
                        userId,
                        credential.getEmail(),
                        newAccessToken,
                        newExpiresAt
                ));
            }
        } catch (Exception e) {
            log.error("Failed to refresh Google token for user {}: {}", userId, e.getMessage());
            if (credential.getAccessToken() != null) {
                return Optional.of(new InternalGoogleTokenResponse(
                        userId,
                        credential.getEmail(),
                        credential.getAccessToken(),
                        credential.getExpiresAt()
                ));
            }
        }

        return Optional.empty();
    }
}
