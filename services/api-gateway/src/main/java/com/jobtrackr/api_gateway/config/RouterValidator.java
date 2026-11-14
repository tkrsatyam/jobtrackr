package com.jobtrackr.api_gateway.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RouterValidator {

    public static final List<String> OPEN_ENDPOINTS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/oauth2",
            "/ping",
            "/api/ingestion/health",
            "/actuator"
    );

    public boolean isSecured(HttpServletRequest request) {
        if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
            return false;
        }

        String path = request.getRequestURI();
        return OPEN_ENDPOINTS.stream()
                .noneMatch(openEndpoint -> path.equals(openEndpoint) || path.startsWith(openEndpoint + "/"));
    }
}
