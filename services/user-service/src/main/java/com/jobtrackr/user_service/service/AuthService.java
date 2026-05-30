package com.jobtrackr.user_service.service;

import com.jobtrackr.user_service.dto.AuthResponse;
import com.jobtrackr.user_service.dto.LoginRequest;
import com.jobtrackr.user_service.dto.RefreshTokenRequest;
import com.jobtrackr.user_service.dto.RegisterRequest;
import com.jobtrackr.user_service.entity.RefreshToken;
import com.jobtrackr.user_service.entity.User;
import com.jobtrackr.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .provider(User.AuthProvider.LOCAL)
                .build();

        user = userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        
        return buildAuthResponse(user, accessToken, refreshToken.getToken());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return buildAuthResponse(user, accessToken, refreshToken.getToken());
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken oldToken = refreshTokenService.validateRefreshToken(request.getRefreshToken());
        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(oldToken);

        User user = oldToken.getUser();
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());

        return buildAuthResponse(user, accessToken, newRefreshToken.getToken());
    }

    public void logout(String accessToken, String refreshToken) {
        jwtService.blacklistToken(accessToken);
        RefreshToken rt = refreshTokenService.validateRefreshToken(refreshToken);
        refreshTokenService.revokeToken(rt);
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }
}
