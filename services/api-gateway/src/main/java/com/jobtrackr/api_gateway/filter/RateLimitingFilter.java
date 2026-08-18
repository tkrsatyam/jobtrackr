package com.jobtrackr.api_gateway.filter;

import com.jobtrackr.api_gateway.config.RouterValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Enforces a per-minute request cap so that a single caller cannot flood the platform.
 * <p>
 * Runs <b>after</b> {@link JwtAuthenticationFilter} so that, for secured routes, the
 * {@code X-User-Id} header set by that filter (from the verified JWT) is already available.
 * For open routes (login/register/etc.), {@link RouterValidator} identifies the request as
 * unsecured and this filter falls back to limiting by client IP instead, to slow down brute
 * force attempts against those endpoints.
 * <p>
 * Uses a Redis <b>fixed-window counter</b>: each caller gets a key scoped to the current
 * 60-second window (e.g. {@code rate-limit:user:42:29184601}), incremented atomically via
 * {@code INCR}. The window's TTL is set only the first time a key is created, so it always
 * expires ~60 seconds after the window opened, regardless of how many requests land in it.
 * This is simpler and safer under concurrent requests than a true sliding-window or token-bucket
 * algorithm, which would need a Lua script (or a library like Redisson) to stay atomic.
 */
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofSeconds(60);
    private static final String RETRY_AFTER_SECONDS = "60";

    private final RouterValidator routerValidator;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.rate-limit.requests-per-minute:100}")
    private int userRequestsPerMinute;

    @Value("${app.rate-limit.ip-requests-per-minute:20}")
    private int ipRequestsPerMinute;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String redisKey;
        int limit;

        if (routerValidator.isSecured(request)) {
            String userId = request.getHeader("X-User-Id");
            if (userId == null) {
                // No verified identity on a secured route means JwtAuthenticationFilter already
                // rejected this request (or it never should have reached here). Nothing for this
                // filter to key on, so let it pass through untouched.
                filterChain.doFilter(request, response);
                return;
            }
            limit = userRequestsPerMinute;
            redisKey = "rate-limit:user:" + userId + ":" + currentWindow();
        } else {
            limit = ipRequestsPerMinute;
            redisKey = "rate-limit:ip:" + extractClientIp(request) + ":" + currentWindow();
        }

        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, WINDOW);
        }

        if (count != null && count > limit) {
            writeRateLimitProblemDetail(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private long currentWindow() {
        return Instant.now().getEpochSecond() / WINDOW.getSeconds();
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // X-Forwarded-For can be a comma-separated chain (client, proxy1, proxy2, ...);
            // the first entry is the original client.
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Writes an RFC 9457 ProblemDetail body for 429 responses, mirroring the shape
     * {@link JwtAuthenticationFilter} already uses for its 401s so error responses stay
     * consistent across this gateway.
     */
    private void writeRateLimitProblemDetail(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String correlationIdField = correlationId != null
                ? ",\"correlationId\":\"" + correlationId + "\""
                : "";
        String body = "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Rate limit exceeded. Try again in 60 seconds.\"" + correlationIdField + "}";

        response.getWriter().write(body);
    }
}