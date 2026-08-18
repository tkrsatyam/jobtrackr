package com.jobtrackr.api_gateway.filter;

import com.jobtrackr.api_gateway.config.RouterValidator;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    @Mock
    private RouterValidator routerValidator;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private FilterChain filterChain;

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter(routerValidator, redisTemplate);
        ReflectionTestUtils.setField(filter, "userRequestsPerMinute", 100);
        ReflectionTestUtils.setField(filter, "ipRequestsPerMinute", 20);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void allowsAuthenticatedRequestUnderTheUserLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/applications");
        request.addHeader("X-User-Id", "user-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(routerValidator.isSecured(request)).thenReturn(true);
        when(valueOperations.increment(anyString())).thenReturn(5L);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void blocksAuthenticatedRequestOverTheUserLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/applications");
        request.addHeader("X-User-Id", "user-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(routerValidator.isSecured(request)).thenReturn(true);
        when(valueOperations.increment(anyString())).thenReturn(101L);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString())
                .contains("\"status\":429")
                .contains("Too Many Requests");
    }

    @Test
    void blocksOpenRouteRequestOverTheIpLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("203.0.113.5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(routerValidator.isSecured(request)).thenReturn(false);
        when(valueOperations.increment(anyString())).thenReturn(21L);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void prefersForwardedForHeaderOverRemoteAddrWhenLimitingByIp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1"); // load balancer's own address
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(routerValidator.isSecured(request)).thenReturn(false);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        filter.doFilter(request, response, filterChain);

        verify(valueOperations).increment(contains("rate-limit:ip:203.0.113.9:"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void setsTtlOnlyOnTheFirstIncrementOfAWindow() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/applications");
        request.addHeader("X-User-Id", "user-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(routerValidator.isSecured(request)).thenReturn(true);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        filter.doFilter(request, response, filterChain);

        verify(redisTemplate).expire(anyString(), eq(Duration.ofSeconds(60)));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doesNotSetTtlAgainOnSubsequentIncrementsInTheSameWindow() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/applications");
        request.addHeader("X-User-Id", "user-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(routerValidator.isSecured(request)).thenReturn(true);
        when(valueOperations.increment(anyString())).thenReturn(2L);

        filter.doFilter(request, response, filterChain);

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void passesThroughSecuredRouteWithNoUserIdWithoutTouchingRedis() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/applications");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(routerValidator.isSecured(request)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(redisTemplate);
    }
}