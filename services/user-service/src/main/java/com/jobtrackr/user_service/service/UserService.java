package com.jobtrackr.user_service.service;

import com.jobtrackr.user_service.dto.ChangePasswordRequest;
import com.jobtrackr.user_service.dto.LinkGoogleRequest;
import com.jobtrackr.user_service.dto.UserProfileDTO;
import com.jobtrackr.user_service.entity.OAuthCredential;
import com.jobtrackr.user_service.entity.User;
import com.jobtrackr.user_service.exception.AccountAlreadyLinkedException;
import com.jobtrackr.user_service.exception.InvalidCurrentPasswordException;
import com.jobtrackr.user_service.exception.PasswordChangeNotAllowedException;
import com.jobtrackr.user_service.exception.UserNotFoundException;
import com.jobtrackr.user_service.repository.OAuthCredentialRepository;
import com.jobtrackr.user_service.repository.RefreshTokenRepository;
import com.jobtrackr.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OAuthCredentialRepository oAuthCredentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AesGcmEncryptionService encryptionService;

    public UserProfileDTO getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return toDto(user);
    }

    public UserProfileDTO updateProfile(UUID userId, UserProfileDTO dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.setFullName(dto.getFullName());
        user.setAvatarUrl(dto.getAvatarUrl());
        return toDto(userRepository.save(user));
    }

    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getProvider() != User.AuthProvider.LOCAL) {
            throw new PasswordChangeNotAllowedException("Password change not available for Google accounts");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidCurrentPasswordException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public UserProfileDTO linkGoogle(UUID userId, LinkGoogleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Ensure the Google account is not already linked to a different user
        Optional<OAuthCredential> existingCred = oAuthCredentialRepository.findByProviderAndProviderUserId(
                User.AuthProvider.GOOGLE, request.getProviderUserId()
        );
        if (existingCred.isPresent() && !existingCred.get().getUser().getId().equals(userId)) {
            throw new AccountAlreadyLinkedException("This Google account is already linked to another JobTrackr user");
        }

        Optional<User> userWithProviderId = userRepository.findByProviderIdAndProvider(
                request.getProviderUserId(), User.AuthProvider.GOOGLE
        );
        if (userWithProviderId.isPresent() && !userWithProviderId.get().getId().equals(userId)) {
            throw new AccountAlreadyLinkedException("This Google account is already linked to another JobTrackr user");
        }

        if (user.getProviderId() == null) {
            user.setProviderId(request.getProviderUserId());
            userRepository.save(user);
        }

        OAuthCredential credential = oAuthCredentialRepository.findByUserIdAndProvider(
                userId, User.AuthProvider.GOOGLE
        ).orElseGet(() -> OAuthCredential.builder()
                .user(user)
                .provider(User.AuthProvider.GOOGLE)
                .build());

        credential.setProviderUserId(request.getProviderUserId());
        credential.setEmail(request.getEmail());

        if (request.getAccessToken() != null) {
            credential.setAccessToken(request.getAccessToken());
        }
        if (request.getExpiresInSeconds() != null) {
            credential.setExpiresAt(Instant.now().plusSeconds(request.getExpiresInSeconds()));
        }
        if (request.getScopes() != null) {
            credential.setScopes(request.getScopes());
        }
        if (request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            credential.setRefreshToken(encryptionService.encrypt(request.getRefreshToken()));
        }

        oAuthCredentialRepository.save(credential);
        log.info("Linked Google account {} to user {}", request.getEmail(), userId);
        return toDto(user);
    }

    @Transactional
    public void unlinkGoogle(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getProvider() == User.AuthProvider.GOOGLE && user.getPassword() == null) {
            throw new IllegalStateException("Cannot unlink Google from an account registered with Google without a password");
        }

        oAuthCredentialRepository.deleteByUserIdAndProvider(userId, User.AuthProvider.GOOGLE);
        if (user.getProvider() == User.AuthProvider.LOCAL) {
            user.setProviderId(null);
            userRepository.save(user);
        }
        log.info("Unlinked Google account from user {}", userId);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        refreshTokenRepository.deleteAllByUser(user);
        userRepository.delete(user);
    }

    private UserProfileDTO toDto(User user) {
        Optional<OAuthCredential> cred = oAuthCredentialRepository.findByUserIdAndProvider(
                user.getId(), User.AuthProvider.GOOGLE
        );
        boolean googleConnected = cred.isPresent() || user.getProvider() == User.AuthProvider.GOOGLE;
        String googleEmail = cred.map(OAuthCredential::getEmail)
                .orElse(user.getProvider() == User.AuthProvider.GOOGLE ? user.getEmail() : null);

        return UserProfileDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole().name())
                .provider(user.getProvider().name())
                .googleConnected(googleConnected)
                .googleEmail(googleEmail)
                .build();
    }
}
