package com.jobtrackr.email_ingestion_service;

import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        UserDetailsServiceAutoConfiguration.class,
        OpenAiAutoConfiguration.class
})
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class EmailIngestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailIngestionServiceApplication.class, args);
    }
}
