package com.jobtrackr.email_ingestion_service.client;

import com.jobtrackr.email_ingestion_service.dto.UserGoogleAccountDTO;
import com.jobtrackr.email_ingestion_service.dto.UserGoogleTokenDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/api/users/internal/google-accounts")
    List<UserGoogleAccountDTO> getGoogleAccounts();

    @GetMapping("/api/users/internal/google-token/{userId}")
    UserGoogleTokenDTO getGoogleToken(@PathVariable("userId") UUID userId);
}
