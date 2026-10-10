package com.jobtrackr.user_service.service;

import com.jobtrackr.user_service.dto.AuthResponse;
import com.jobtrackr.user_service.dto.LoginRequest;
import com.jobtrackr.user_service.dto.RefreshTokenRequest;
import com.jobtrackr.user_service.dto.RegisterRequest;
import com.jobtrackr.user_service.entity.RefreshToken;
import com.jobtrackr.user_service.entity.User;
import com.jobtrackr.user_service.exception.EmailAlreadyInUseException;
import com.jobtrackr.user_service.exception.InvalidCredentialsException;
import com.jobtrackr.user_service.exception.InvalidRefreshTokenException;
import com.jobtrackr.user_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {
    
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    
    private AuthService authService;
    private User existingUser;
    
    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService);
        
        existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("jan.niepsuj@example.com")
                .password("encoded-password")
                .fullName("Jan Niepsuj")
                .provider(User.AuthProvider.LOCAL)
                .role(User.Role.USER)
                .build();
    }
    
    @Test
    void registerSuccess() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("jan.niepsuj@example.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setFullName("Jan Niepsuj");
        
        when(userRepository.existsByEmail("jan.niepsuj@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        when(jwtService.generateAccessToken(any(), anyString(), anyString())).thenReturn("access-token");

        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-token")
                .user(existingUser)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(refreshTokenService.createRefreshToken(existingUser)).thenReturn(refreshToken);

        AuthResponse response = authService.register(request);
        
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getEmail()).isEqualTo("jan.niepsuj@example.com");
        verify(userRepository).save(any(User.class));
    }
    
    @Test
    void registerDuplicateEmailViaExistsCheckThrows() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("jan.niepsuj@example.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setFullName("Jan Niepsuj");

        when(userRepository.existsByEmail("jan.niepsuj@example.com")).thenReturn(true);
        
        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(EmailAlreadyInUseException.class);
        
        verify(userRepository, never()).save(any());
    }
    
    @Test
    void registerDuplicateEmailViaRaceOnSaveThrows() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("jan.niepsuj@example.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setFullName("Jan Niepsuj");

        when(userRepository.existsByEmail("jan.niepsuj@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        
        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(EmailAlreadyInUseException.class);
    }
    
    @Test
    void loginSuccess() {
        LoginRequest request = new LoginRequest();
        request.setEmail("jan.niepsuj@example.com");
        request.setPassword("password123");
        
        when(userRepository.findByEmail("jan.niepsuj@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtService.generateAccessToken(any(), anyString(), anyString())).thenReturn("access-token");
        
        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-token")
                .user(existingUser)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(refreshTokenService.createRefreshToken(existingUser)).thenReturn(refreshToken);
        
        AuthResponse response = authService.login(request);
        
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
    }
    
    @Test
    void loginBadPasswordThrows() {
        LoginRequest request = new LoginRequest();
        request.setEmail("jan.niepsuj@example.com");
        request.setPassword("wrong-password");
        
        when(userRepository.findByEmail("jan.niepsuj@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrong-password", "encoded-password"))
                .thenReturn(false);
        
        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);
    }
    
    @Test
    void loginUserNotFoundThrows() {
        LoginRequest request = new LoginRequest();
        request.setEmail("nobody@example.com");
        request.setPassword("password123");
        
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        
        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);
        
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
    
    @Test
    void refreshWithValidTokenRotatesAndReturnsNewTokens() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh-token");
        
        RefreshToken oldToken = RefreshToken.builder()
                .token("old-refresh-token")
                .user(existingUser)
                .expiresAt(Instant.now().plusSeconds(60))
                .revoked(false)
                .build();

        RefreshToken newToken = RefreshToken.builder()
                .token("new-refresh-token")
                .user(existingUser)
                .expiresAt(Instant.now().plusSeconds(60))
                .revoked(false)
                .build();
        
        when(refreshTokenService.validateRefreshToken("old-refresh-token")).thenReturn(oldToken);
        when(refreshTokenService.rotateRefreshToken(oldToken)).thenReturn(newToken);
        when(jwtService.generateAccessToken(any(), anyString(), anyString())).thenReturn("new-access-token");
        
        AuthResponse response = authService.refresh(request);
        
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
    }
    
    @Test
    void refreshWithInvalidTokenThrows() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("bad-token");
        
        when(refreshTokenService.validateRefreshToken("bad-token"))
                .thenThrow(new InvalidRefreshTokenException("Invalid or expired refresh token"));
        
        assertThatThrownBy(() -> authService.refresh(request)).isInstanceOf(InvalidRefreshTokenException.class);
    }
    
    @Test
    void logoutBlacklistsAccessTokenAndRevokesRefreshToken() {
        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-token")
                .user(existingUser)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        
        when(refreshTokenService.validateRefreshToken("refresh-token")).thenReturn(refreshToken);

        authService.logout("access-token", "refresh-token");
        
        verify(jwtService).blacklistToken("access-token");
        verify(refreshTokenService).revokeToken(refreshToken);
    }
}
