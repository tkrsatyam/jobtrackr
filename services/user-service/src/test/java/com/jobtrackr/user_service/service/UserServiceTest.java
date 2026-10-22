package com.jobtrackr.user_service.service;

import com.jobtrackr.user_service.dto.LinkGoogleRequest;
import com.jobtrackr.user_service.dto.UserProfileDTO;
import com.jobtrackr.user_service.entity.OAuthCredential;
import com.jobtrackr.user_service.entity.User;
import com.jobtrackr.user_service.exception.AccountAlreadyLinkedException;
import com.jobtrackr.user_service.repository.OAuthCredentialRepository;
import com.jobtrackr.user_service.repository.RefreshTokenRepository;
import com.jobtrackr.user_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuthCredentialRepository oAuthCredentialRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AesGcmEncryptionService encryptionService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                oAuthCredentialRepository,
                refreshTokenRepository,
                passwordEncoder,
                encryptionService
        );
    }

    @Test
    @DisplayName("Should return googleConnected true and googleEmail when OAuth credential exists")
    void shouldReturnGoogleConnectedTrueWhenCredentialExists() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("satyam@local.com")
                .provider(User.AuthProvider.LOCAL)
                .role(User.Role.USER)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        OAuthCredential cred = OAuthCredential.builder()
                .email("satyam@gmail.com")
                .provider(User.AuthProvider.GOOGLE)
                .build();
        when(oAuthCredentialRepository.findByUserIdAndProvider(userId, User.AuthProvider.GOOGLE))
                .thenReturn(Optional.of(cred));

        UserProfileDTO profile = userService.getProfile(userId);

        assertThat(profile.isGoogleConnected()).isTrue();
        assertThat(profile.getGoogleEmail()).isEqualTo("satyam@gmail.com");
        assertThat(profile.getProvider()).isEqualTo("LOCAL");
    }

    @Test
    @DisplayName("Should return googleConnected false when LOCAL user has no OAuth credential")
    void shouldReturnGoogleConnectedFalseWhenNoCredential() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("satyam@local.com")
                .provider(User.AuthProvider.LOCAL)
                .role(User.Role.USER)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(oAuthCredentialRepository.findByUserIdAndProvider(userId, User.AuthProvider.GOOGLE))
                .thenReturn(Optional.empty());

        UserProfileDTO profile = userService.getProfile(userId);

        assertThat(profile.isGoogleConnected()).isFalse();
        assertThat(profile.getGoogleEmail()).isNull();
    }

    @Test
    @DisplayName("Should link Google account successfully and encrypt refresh token")
    void shouldLinkGoogleAccountSuccessfully() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("satyam@local.com")
                .provider(User.AuthProvider.LOCAL)
                .role(User.Role.USER)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        LinkGoogleRequest request = LinkGoogleRequest.builder()
                .providerUserId("google-sub-123")
                .email("satyam@gmail.com")
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .expiresInSeconds(3600L)
                .scopes("openid email")
                .build();

        when(oAuthCredentialRepository.findByProviderAndProviderUserId(User.AuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByProviderIdAndProvider("google-sub-123", User.AuthProvider.GOOGLE))
                .thenReturn(Optional.empty());
        when(oAuthCredentialRepository.findByUserIdAndProvider(userId, User.AuthProvider.GOOGLE))
                .thenReturn(Optional.empty());
        when(encryptionService.encrypt("mock-refresh-token")).thenReturn("encrypted-refresh-token");

        UserProfileDTO result = userService.linkGoogle(userId, request);

        verify(userRepository).save(user);
        assertThat(user.getProviderId()).isEqualTo("google-sub-123");

        ArgumentCaptor<OAuthCredential> credCaptor = ArgumentCaptor.forClass(OAuthCredential.class);
        verify(oAuthCredentialRepository).save(credCaptor.capture());
        OAuthCredential savedCred = credCaptor.getValue();
        assertThat(savedCred.getEmail()).isEqualTo("satyam@gmail.com");
        assertThat(savedCred.getRefreshToken()).isEqualTo("encrypted-refresh-token");
        assertThat(savedCred.getAccessToken()).isEqualTo("mock-access-token");
    }

    @Test
    @DisplayName("Should throw AccountAlreadyLinkedException when Google ID already linked to another user")
    void shouldThrowWhenGoogleIdAlreadyLinkedToAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        User user = User.builder().id(userId).email("satyam@local.com").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        User otherUser = User.builder().id(otherUserId).build();
        OAuthCredential otherCred = OAuthCredential.builder().user(otherUser).build();
        when(oAuthCredentialRepository.findByProviderAndProviderUserId(User.AuthProvider.GOOGLE, "google-sub-conflict"))
                .thenReturn(Optional.of(otherCred));

        LinkGoogleRequest request = LinkGoogleRequest.builder()
                .providerUserId("google-sub-conflict")
                .email("satyam@gmail.com")
                .build();

        assertThatThrownBy(() -> userService.linkGoogle(userId, request))
                .isInstanceOf(AccountAlreadyLinkedException.class)
                .hasMessageContaining("already linked to another JobTrackr user");

        verify(oAuthCredentialRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should unlink Google account for LOCAL user")
    void shouldUnlinkGoogleAccountForLocalUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("satyam@local.com")
                .provider(User.AuthProvider.LOCAL)
                .providerId("google-sub-123")
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.unlinkGoogle(userId);

        verify(oAuthCredentialRepository).deleteByUserIdAndProvider(userId, User.AuthProvider.GOOGLE);
        verify(userRepository).save(user);
        assertThat(user.getProviderId()).isNull();
    }

    @Test
    @DisplayName("Should prevent unlinking Google from GOOGLE user without a password")
    void shouldPreventUnlinkForGoogleUserWithoutPassword() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("satyam@gmail.com")
                .provider(User.AuthProvider.GOOGLE)
                .password(null)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.unlinkGoogle(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot unlink Google from an account registered with Google without a password");

        verify(oAuthCredentialRepository, never()).deleteByUserIdAndProvider(any(), any());
    }
}
