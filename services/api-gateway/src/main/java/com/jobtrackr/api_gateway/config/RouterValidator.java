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
            "/api/auth/oauth2"
    );

    public boolean isSecured(HttpServletRequest request) {
        return OPEN_ENDPOINTS.stream()
                .noneMatch(uri -> request.getRequestURI().contains(uri));
    }
}
