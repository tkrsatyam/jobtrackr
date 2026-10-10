package com.jobtrackr.user_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private JwtService jwtService;

    private static final String SECRET =
            "test-secret-key-for-unit-tests-only-not-for-production-use-0123456789";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(redisTemplate);
        ReflectionTestUtils.setField(jwtService, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 900000L);
    }

    @Test
    void generateAccessTokenThenExtractSubjectRoundTrips() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, "jane@example.com", "USER");

        assertThat(jwtService.extractClaims(token).getSubject()).isEqualTo(userId.toString());
    }

    @Test
    void isTokenValidForFreshUnblacklistedTokenIsTrue() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        String token = jwtService.generateAccessToken(UUID.randomUUID(), "jane@example.com", "USER");

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValidForBlacklistedTokenIsFalse() {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        String token = jwtService.generateAccessToken(UUID.randomUUID(), "jane@example.com", "USER");

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValidForMalformedTokenIsFalse() {
        assertThat(jwtService.isTokenValid("not-a-real-jwt")).isFalse();
    }

    @Test
    void blacklistTokenStoresInRedisWithRemainingTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String token = jwtService.generateAccessToken(UUID.randomUUID(), "jane@example.com", "USER");

        jwtService.blacklistToken(token);

        verify(valueOperations).set(
                startsWith("blacklist:"),
                eq("true"),
                anyLong(),
                eq(TimeUnit.MILLISECONDS)
        );
    }
}