package com.jobtrackr.user_service.config;

import com.jobtrackr.user_service.entity.OAuthCredential;
import com.jobtrackr.user_service.entity.User;
import com.jobtrackr.user_service.repository.OAuthCredentialRepository;
import com.jobtrackr.user_service.repository.UserRepository;
import com.jobtrackr.user_service.service.AesGcmEncryptionService;
import com.jobtrackr.user_service.service.JwtService;
import com.jobtrackr.user_service.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public static final String OAUTH2_LINK_USER_ID_COOKIE = "oauth2_link_user_id";

    @Value("${application.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    private final UserRepository userRepository;
    private final OAuthCredentialRepository oAuthCredentialRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final AesGcmEncryptionService encryptionService;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");
        String googleId = oAuth2User.getAttribute("sub");

        log.info("Processing OAuth2 callback for email: {} with provider ID: {}", email, googleId);

        // Check if this is an account linking request for an already authenticated user
        Cookie linkCookie = getCookie(request, OAUTH2_LINK_USER_ID_COOKIE);
        if (linkCookie != null && linkCookie.getValue() != null && !linkCookie.getValue().isBlank()) {
            String linkUserIdStr = linkCookie.getValue();
            deleteCookie(response, OAUTH2_LINK_USER_ID_COOKIE);
            handleAccountLinking(request, response, oauthToken, targetUserId(linkUserIdStr), googleId, email, picture);
            return;
        }

        // Standard login/registration flow
        User user = userRepository.findByEmail(email).map(existingUser -> {
            if (existingUser.getProviderId() == null) {
                existingUser.setProviderId(googleId);
            }
            if (existingUser.getFullName() == null && name != null) {
                existingUser.setFullName(name);
            }
            if (existingUser.getAvatarUrl() == null && picture != null) {
                existingUser.setAvatarUrl(picture);
            }
            return userRepository.save(existingUser);
        }).orElseGet(() -> {
            User newUser = User.builder()
                    .email(email)
                    .fullName(name)
                    .avatarUrl(picture)
                    .provider(User.AuthProvider.GOOGLE)
                    .providerId(googleId)
                    .role(User.Role.USER)
                    .build();
            return userRepository.save(newUser);
        });

        // Persist/update OAuth credentials (refresh token encrypted at rest)
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
        );

        if (authorizedClient != null) {
            saveOAuthCredentials(user, googleId, email, authorizedClient);
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = refreshTokenService.createRefreshToken(user).getToken();

        String redirectUrl = frontendUrl + "/oauth2/callback"
                + "?accessToken=" + accessToken
                + "&refreshToken=" + refreshToken;

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private void handleAccountLinking(HttpServletRequest request,
                                      HttpServletResponse response,
                                      OAuth2AuthenticationToken oauthToken,
                                      UUID targetUserId,
                                      String googleId,
                                      String email,
                                      String picture) throws IOException {
        if (targetUserId == null) {
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/settings?error=invalid_link_request");
            return;
        }

        // Conflict check: Google ID already linked to another user in OAuth credentials
        Optional<OAuthCredential> existingCred = oAuthCredentialRepository.findByProviderAndProviderUserId(
                User.AuthProvider.GOOGLE, googleId
        );
        if (existingCred.isPresent() && !existingCred.get().getUser().getId().equals(targetUserId)) {
            log.warn("Google account {} is already linked to user {}", googleId, existingCred.get().getUser().getId());
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/settings?error=account_already_linked");
            return;
        }

        // Conflict check: Google ID already assigned to another user entity
        Optional<User> userWithProviderId = userRepository.findByProviderIdAndProvider(
                googleId, User.AuthProvider.GOOGLE
        );
        if (userWithProviderId.isPresent() && !userWithProviderId.get().getId().equals(targetUserId)) {
            log.warn("Google account {} is already registered by user {}", googleId, userWithProviderId.get().getId());
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/settings?error=account_already_linked");
            return;
        }

        Optional<User> targetUserOpt = userRepository.findById(targetUserId);
        if (targetUserOpt.isEmpty()) {
            log.warn("Target user for linking not found: {}", targetUserId);
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/settings?error=user_not_found");
            return;
        }

        User targetUser = targetUserOpt.get();
        if (targetUser.getProviderId() == null) {
            targetUser.setProviderId(googleId);
        }
        if (targetUser.getAvatarUrl() == null && picture != null) {
            targetUser.setAvatarUrl(picture);
        }
        userRepository.save(targetUser);

        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
        );
        if (authorizedClient != null) {
            saveOAuthCredentials(targetUser, googleId, email, authorizedClient);
        }

        log.info("Successfully linked Google account {} to user {}", email, targetUserId);
        getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/settings?linked=true");
    }

    private UUID targetUserId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid user ID string: {}", value);
            return null;
        }
    }

    private void saveOAuthCredentials(User user,
                                      String googleId,
                                      String email,
                                      OAuth2AuthorizedClient authorizedClient) {
        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

        Optional<OAuthCredential> existingCredOpt = oAuthCredentialRepository.findByUserIdAndProvider(
                user.getId(), User.AuthProvider.GOOGLE
        );

        OAuthCredential credential = existingCredOpt.orElseGet(() -> OAuthCredential.builder()
                .user(user)
                .provider(User.AuthProvider.GOOGLE)
                .build());

        credential.setProviderUserId(googleId);
        credential.setEmail(email);

        if (accessToken != null) {
            credential.setAccessToken(accessToken.getTokenValue());
            credential.setExpiresAt(accessToken.getExpiresAt());
            if (accessToken.getScopes() != null && !accessToken.getScopes().isEmpty()) {
                credential.setScopes(String.join(" ", accessToken.getScopes()));
            }
        }

        if (refreshToken != null && refreshToken.getTokenValue() != null) {
            credential.setRefreshToken(encryptionService.encrypt(refreshToken.getTokenValue()));
        }

        oAuthCredentialRepository.save(credential);
        log.info("Persisted encrypted OAuth credentials for user {}", user.getId());
    }

    private Cookie getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return cookie;
                }
            }
        }
        return null;
    }

    private void deleteCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }
}
