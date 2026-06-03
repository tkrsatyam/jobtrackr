package com.jobtrackr.application_service.util;

import com.jobtrackr.application_service.exception.UnauthorizedAccessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserContextHolder {

    public UUID getUserId(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new UnauthorizedAccessException("Missing X-User-Id header");
        }
        try {
            return UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedAccessException("Invalid X-User-Id header value");
        }
    }

    public String getUserEmail(HttpServletRequest request) {
        return request.getHeader("X-User-Email");
    }

    public String getUserRole(HttpServletRequest request) {
        return request.getHeader("X-User-Role");
    }
}