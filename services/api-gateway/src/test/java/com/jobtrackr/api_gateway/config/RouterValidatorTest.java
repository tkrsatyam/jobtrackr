package com.jobtrackr.api_gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RouterValidatorTest {

    private RouterValidator routerValidator;

    @BeforeEach
    void setUp() {
        routerValidator = new RouterValidator();
    }

    @Test
    void optionsRequestsAreNotSecured() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/applications");
        assertThat(routerValidator.isSecured(request)).isFalse();
    }

    @Test
    void exactOpenEndpointsAreNotSecured() {
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("GET", "/ping"))).isFalse();
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("POST", "/api/auth/login"))).isFalse();
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("POST", "/api/auth/register"))).isFalse();
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("POST", "/api/auth/refresh"))).isFalse();
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("GET", "/actuator"))).isFalse();
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("GET", "/api/ingestion/health"))).isFalse();
    }

    @Test
    void subpathsOfOpenEndpointsAreNotSecured() {
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("GET", "/actuator/health"))).isFalse();
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("GET", "/actuator/prometheus"))).isFalse();
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("GET", "/actuator/health/liveness"))).isFalse();
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("GET", "/actuator/health/readiness"))).isFalse();
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("GET", "/api/auth/oauth2/code/google"))).isFalse();
    }

    @Test
    void securedEndpointsAreSecured() {
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("GET", "/api/applications"))).isTrue();
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("GET", "/api/users/me"))).isTrue();
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("POST", "/api/applications/review"))).isTrue();
    }

    @Test
    void endpointsContainingOpenEndpointSubstringAreSecured() {
        // Prevents authentication bypass where "/actuator" appears as a substring in secured routes
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("GET", "/api/applications/actuator"))).isTrue();
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("GET", "/api/users/me/actuator"))).isTrue();
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("GET", "/api/applications/123/actuator"))).isTrue();
        assertThat(routerValidator.isSecured(new MockHttpServletRequest("GET", "/api/ping-test"))).isTrue();
    }
}
