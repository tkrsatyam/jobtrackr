package com.jobtrackr.application_service.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.jobtrackr.application_service.client")
public class OpenFeignConfig {
}
