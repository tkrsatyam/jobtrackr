package com.jobtrackr.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

/**
 * Direct container/host routing via Docker internal DNS.
 * Service target URLs are read from environment properties with defaults
 * matching Docker Compose container hostnames.
 */
@Configuration
public class GatewayRoutesConfig {

    @Value("${app.services.user-service-url:http://user-service:8081}")
    private String userServiceUrl;

    @Value("${app.services.application-service-url:http://application-service:8082}")
    private String applicationServiceUrl;

    @Value("${app.services.reminder-service-url:http://reminder-service:8083}")
    private String reminderServiceUrl;

    @Value("${app.services.document-service-url:http://document-service:8084}")
    private String documentServiceUrl;

    @Value("${app.services.contact-service-url:http://contact-service:8085}")
    private String contactServiceUrl;

    @Value("${app.services.notification-service-url:http://notification-service:8086}")
    private String notificationServiceUrl;

    @Value("${app.services.analytics-service-url:http://analytics-service:8087}")
    private String analyticsServiceUrl;

    @Value("${app.services.email-ingestion-service-url:http://email-ingestion-service:8088}")
    private String emailIngestionServiceUrl;

    @Bean
    public RouterFunction<ServerResponse> userServiceRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/auth/**").or(RequestPredicates.path("/api/users/**")),
                        HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(URI.create(userServiceUrl)))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> applicationServiceRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/applications/**"),
                        HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(URI.create(applicationServiceUrl)))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> reminderServiceRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/reminders/**"),
                        HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(URI.create(reminderServiceUrl)))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> documentServiceRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/documents/**"),
                        HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(URI.create(documentServiceUrl)))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> contactServiceRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/contacts/**"),
                        HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(URI.create(contactServiceUrl)))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> notificationServiceRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/notifications/**"),
                        HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(URI.create(notificationServiceUrl)))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> analyticsServiceRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/analytics/**"),
                        HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(URI.create(analyticsServiceUrl)))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> emailIngestionServiceRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/ingestion/**"),
                        HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(URI.create(emailIngestionServiceUrl)))
                .build();
    }
}
