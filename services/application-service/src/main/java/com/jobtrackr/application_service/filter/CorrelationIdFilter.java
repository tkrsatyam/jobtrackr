package com.jobtrackr.application_service.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads the {@code X-Correlation-Id} header set by API Gateway and places it in {@link MDC} so
 * it appears in this service's log lines and can be attached to error responses (see
 * {@link com.jobtrackr.application_service.exception.CorrelationIdProblemDetailAdvice}).
 * <p>
 * Falls back to generating a new ID if the header is missing, so this still works for requests
 * made directly to this service (e.g. local development/testing without the gateway in front).
 * <p>
 * {@code @Order(HIGHEST_PRECEDENCE)} ensures this runs before Spring Security's filter chain, so
 * the correlation ID is available in MDC for the whole request, including any security rejections.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}