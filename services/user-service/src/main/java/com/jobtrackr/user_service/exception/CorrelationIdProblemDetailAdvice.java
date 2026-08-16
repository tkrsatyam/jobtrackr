package com.jobtrackr.user_service.exception;

import com.jobtrackr.user_service.filter.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Adds the current request's correlation ID as a {@code correlationId} extension property on
 * every {@link ProblemDetail} error response, per the JD-109 error contract (see
 * {@code API_CONTRACTS.md}). Implemented as a single {@link ResponseBodyAdvice} rather than
 * editing every {@code @ExceptionHandler} in {@link GlobalExceptionHandler}, so new handlers get
 * this for free.
 */
@RestControllerAdvice
public class CorrelationIdProblemDetailAdvice implements ResponseBodyAdvice<ProblemDetail> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ProblemDetail.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public ProblemDetail beforeBodyWrite(ProblemDetail body, MethodParameter returnType, MediaType selectedContentType,
                                         Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                         ServerHttpRequest request, ServerHttpResponse response) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            body.setProperty("correlationId", correlationId);
        }
        return body;
    }
}