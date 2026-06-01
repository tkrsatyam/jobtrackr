package com.jobtrackr.api_gateway.config;

import org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouterFunction<ServerResponse> userServiceRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/auth/**").or(RequestPredicates.path("/api/users/**")),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("user-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> applicationServiceRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/applications/**"),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("application-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> reminderServiceRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/reminders/**"),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("reminder-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> documentServiceRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/documents/**"),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("document-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> contactServiceRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/contacts/**"),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("contact-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> notificationServiceRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/notifications/**"),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("notification-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> analyticsServiceRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/analytics/**"),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("analytics-service"))
                .build();
    }
}
