package com.jobtrackr.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

/**
 * Production routing — only user-service and application-service are
 * deployed to Render in this phase. reminder/document/contact/notification/
 * analytics services have no routes here yet; requests to those paths will
 * 404 until those services are deployed and a route is added.
 *
 * Unlike GatewayRoutesConfig, this does NOT use lb("service-name") because
 * Render doesn't give us a shared Eureka registry across services. Each
 * service's full URL (https://...onrender.com) is read from an environment
 * variable and set as the forwarding target via BeforeFilterFunctions.uri(),
 * with HandlerFunctions.https() as the actual proxy handler.
 */
@Configuration
@Profile("prod")
public class GatewayRoutesProdConfig {

    @Value("${app.services.user-service-url}")
    private String userServiceUrl;

    @Value("${app.services.application-service-url}")
    private String applicationServiceUrl;

    @Bean
    public RouterFunction<ServerResponse> userServiceProdRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/auth/**").or(RequestPredicates.path("/api/users/**")),
                        HandlerFunctions.https())
                .before(BeforeFilterFunctions.uri(URI.create(userServiceUrl)))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> applicationServiceProdRoute() {
        return RouterFunctions.route()
                .route(RequestPredicates.path("/api/applications/**"),
                        HandlerFunctions.https())
                .before(BeforeFilterFunctions.uri(URI.create(applicationServiceUrl)))
                .build();
    }
}