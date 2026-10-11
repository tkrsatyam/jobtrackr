package com.jobtrackr.user_service.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        // Prevent MDC state from leaking into subsequent tests.
        MDC.clear();
    }

    @Test
    void incomingHeaderIsUsedAsCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "incoming-id-123");

        final String[] captured = new String[1];
        FilterChain chain = (req, res) -> captured[0] = MDC.get(CorrelationIdFilter.MDC_KEY);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(captured[0]).isEqualTo("incoming-id-123");
        // MDC must be cleared after the filter chain completes.
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void missingHeaderGeneratesNewCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        final String[] captured = new String[1];
        FilterChain chain = (req, res) -> captured[0] = MDC.get(CorrelationIdFilter.MDC_KEY);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(captured[0]).isNotBlank();
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void blankHeaderGeneratesNewCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "   ");

        final String[] captured = new String[1];
        FilterChain chain = (req, res) -> captured[0] = MDC.get(CorrelationIdFilter.MDC_KEY);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(captured[0]).isNotBlank();
    }

    @Test
    void mdcIsClearedEvenWhenDownstreamThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        FilterChain chain = (req, res) -> { throw new RuntimeException("downstream failure"); };

        try {
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        } catch (Exception ignored) {
            // expected
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}