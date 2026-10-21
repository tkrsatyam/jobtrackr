package com.jobtrackr.user_service.config;

import com.jobtrackr.user_service.entity.OAuthCredential;
import com.jobtrackr.user_service.entity.RefreshToken;
import com.jobtrackr.user_service.entity.User;
import com.jobtrackr.user_service.repository.OAuthCredentialRepository;
import com.jobtrackr.user_service.repository.UserRepository;
import com.jobtrackr.user_service.service.AesGcmEncryptionService;
import com.jobtrackr.user_service.service.JwtService;
import com.jobtrackr.user_service.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuthCredentialRepository oAuthCredentialRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private OAuth2AuthorizedClientService authorizedClientService;

    @Mock
    private AesGcmEncryptionService encryptionService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private OAuth2AuthenticationToken authenticationToken;

    @Mock
    private OAuth2User oAuth2User;

    @Mock
    private OAuth2AuthorizedClient authorizedClient;

    private OAuth2SuccessHandler successHandler;

    @BeforeEach
    void setUp() {
        successHandler = new OAuth2SuccessHandler(
                userRepository,
                oAuthCredentialRepository,
                jwtService,
                refreshTokenService,
                authorizedClientService,
                encryptionService
        );
        ReflectionTestUtils.setField(successHandler, "frontendUrl", "http://localhost:4200");
    }

    @Test
    @DisplayName("Should create new user, save encrypted OAuth credentials, and redirect to frontend callback on new Google login")
    void shouldCreateUserAndPersistEncryptedTokens() throws IOException {
        String email = "satyam@example.com";
        String googleId = "google-sub-123456";
        String name = "Satyam Kumar";
        String picture = "https://example.com/avatar.png";

        when(authenticationToken.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getAttribute("email")).thenReturn(email);
        when(oAuth2User.getAttribute("name")).thenReturn(name);
        when(oAuth2User.getAttribute("picture")).thenReturn(picture);
        when(oAuth2User.getAttribute("sub")).thenReturn(googleId);

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        UUID userId = UUID.randomUUID();
        User savedUser = User.builder()
                .id(userId)
                .email(email)
                .fullName(name)
                .avatarUrl(picture)
                .provider(User.AuthProvider.GOOGLE)
                .providerId(googleId)
                .role(User.Role.USER)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        when(authenticationToken.getAuthorizedClientRegistrationId()).thenReturn("google");
        when(authenticationToken.getName()).thenReturn("google-principal");
        when(authorizedClientService.loadAuthorizedClient("google", "google-principal")).thenReturn(authorizedClient);

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "ya29.google-access-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Set.of("openid", "email", "profile", "https://www.googleapis.com/auth/gmail.readonly")
        );
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken("1//04_google_refresh_token", Instant.now());
        when(authorizedClient.getAccessToken()).thenReturn(accessToken);
        when(authorizedClient.getRefreshToken()).thenReturn(refreshToken);

        when(oAuthCredentialRepository.findByUserIdAndProvider(userId, User.AuthProvider.GOOGLE))
                .thenReturn(Optional.empty());
        when(encryptionService.encrypt("1//04_google_refresh_token")).thenReturn("encrypted_refresh_token_base64");

        when(jwtService.generateAccessToken(userId, email, "USER")).thenReturn("mock-jwt-access-token");
        RefreshToken appRefreshToken = RefreshToken.builder().token("mock-app-refresh-token").build();
        when(refreshTokenService.createRefreshToken(savedUser)).thenReturn(appRefreshToken);

        when(response.encodeRedirectURL(any())).thenAnswer(invocation -> invocation.getArgument(0));

        successHandler.onAuthenticationSuccess(request, response, authenticationToken);

        // Verify User was saved
        verify(userRepository).save(any(User.class));

        // Verify OAuthCredential saved with encrypted token
        ArgumentCaptor<OAuthCredential> credCaptor = ArgumentCaptor.forClass(OAuthCredential.class);
        verify(oAuthCredentialRepository).save(credCaptor.capture());
        OAuthCredential savedCred = credCaptor.getValue();
        assertThat(savedCred.getEmail()).isEqualTo(email);
        assertThat(savedCred.getProvider()).isEqualTo(User.AuthProvider.GOOGLE);
        assertThat(savedCred.getProviderUserId()).isEqualTo(googleId);
        assertThat(savedCred.getRefreshToken()).isEqualTo("encrypted_refresh_token_base64");
        assertThat(savedCred.getAccessToken()).isEqualTo("ya29.google-access-token");

        // Verify redirect to frontend callback
        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).startsWith("http://localhost:4200/oauth2/callback");
        assertThat(redirectUrl).contains("accessToken=mock-jwt-access-token");
        assertThat(redirectUrl).contains("refreshToken=mock-app-refresh-token");
    }
}
