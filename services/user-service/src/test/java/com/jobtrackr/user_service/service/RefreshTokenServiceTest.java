package com.jobtrackr.user_service.service;

import com.jobtrackr.user_service.entity.RefreshToken;
import com.jobtrackr.user_service.entity.User;
import com.jobtrackr.user_service.exception.InvalidRefreshTokenException;
import com.jobtrackr.user_service.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;
    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository);
        // Inject the @Value field that Spring would normally set from properties.
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpiration", 604800000L);

        user = User.builder().id(UUID.randomUUID()).email("jane@example.com").build();
    }

    @Test
    void createRefreshTokenSavesAndReturnsToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken token = refreshTokenService.createRefreshToken(user);

        assertThat(token.getUser()).isEqualTo(user);
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getExpiresAt()).isAfter(Instant.now());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void validateRefreshTokenValidTokenReturnsIt() {
        RefreshToken token = RefreshToken.builder()
                .token("valid-token")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(60))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        assertThat(refreshTokenService.validateRefreshToken("valid-token")).isEqualTo(token);
    }

    @Test
    void validateRefreshTokenExpiredTokenThrows() {
        RefreshToken token = RefreshToken.builder()
                .token("expired-token")
                .user(user)
                .expiresAt(Instant.now().minusSeconds(60))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("expired-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void validateRefreshTokenRevokedTokenThrows() {
        RefreshToken token = RefreshToken.builder()
                .token("revoked-token")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(60))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("revoked-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void validateRefreshTokenUnknownTokenThrows() {
        when(refreshTokenRepository.findByToken("unknown-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("unknown-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revokeTokenMarksRevokedAndSaves() {
        RefreshToken token = RefreshToken.builder()
                .token("some-token")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(60))
                .revoked(false)
                .build();

        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        refreshTokenService.revokeToken(token);

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void rotateRefreshTokenRevokesOldAndCreatesNew() {
        RefreshToken oldToken = RefreshToken.builder()
                .token("old-token")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(60))
                .revoked(false)
                .build();

        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken newToken = refreshTokenService.rotateRefreshToken(oldToken);

        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(newToken.getUser()).isEqualTo(user);
        assertThat(newToken.getToken()).isNotEqualTo(oldToken.getToken());
        // save is called twice: once to revoke old, once to persist new
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }
}