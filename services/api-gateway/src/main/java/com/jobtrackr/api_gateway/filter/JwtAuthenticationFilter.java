package com.jobtrackr.api_gateway.filter;

import com.jobtrackr.api_gateway.config.RouterValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final RouterValidator routerValidator;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!routerValidator.isSecured(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeProblemDetail(response, "Missing or malformed Authorization header");
            return;
        }

        String token = authHeader.substring(7);

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            writeProblemDetail(response, "Invalid or expired token");
            return;
        }

        Boolean isBlacklisted = redisTemplate.hasKey("blacklist:" + token);
        if (Boolean.TRUE.equals(isBlacklisted)) {
            writeProblemDetail(response, "Token has been revoked");
            return;
        }

        MutableHttpServletRequest mutableRequest = new MutableHttpServletRequest(request);
        mutableRequest.putHeader("X-User-Id", claims.getSubject());
        mutableRequest.putHeader("X-User-Email", claims.get("email", String.class));
        mutableRequest.putHeader("X-User-Role", claims.get("role", String.class));

        filterChain.doFilter(mutableRequest, response);
    }

    /**
     * Writes a minimal RFC 9457 ProblemDetail body for 401 responses raised in this filter.
     * Built manually rather than via an injected ObjectMapper/JsonMapper bean, since this
     * filter runs ahead of DispatcherServlet and the coexisting Jackson 2 (jjwt-jackson) and
     * Jackson 3 (Boot 4 default) dependencies on this service's classpath make relying on
     * an auto-configured mapper bean here brittle.
     */
    private void writeProblemDetail(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String escapedDetail = detail.replace("\\", "\\\\").replace("\"", "\\\"");
        String body = "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"" + escapedDetail + "\"}";

        response.getWriter().write(body);
    }
}
