package com.jobtrackr.api_gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Ensures every request carries a correlation ID that downstream services can use to tie their
 * log lines back to the same originating request.
 * <p>
 * Reads {@code X-Correlation-Id} from the inbound request; generates a new UUID if the client
 * didn't send one. The ID is forwarded downstream as a header (via {@link MutableHttpServletRequest})
 * and placed in {@link MDC} so it's included in this service's own log lines for the request.
 * <p>
 * Runs before {@link JwtAuthenticationFilter} so the ID is available for every request, including
 * ones that get rejected with 401 before reaching a backend service.
 */
@Slf4j
@Component
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

        MutableHttpServletRequest mutableRequest = new MutableHttpServletRequest(request);
        mutableRequest.putHeader(HEADER_NAME, correlationId);

        MDC.put(MDC_KEY, correlationId);
        try {
            log.info("{} {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(mutableRequest, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}