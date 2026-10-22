package com.jobtrackr.email_ingestion_service.controller;

import com.jobtrackr.email_ingestion_service.dto.IngestionSyncResponse;
import com.jobtrackr.email_ingestion_service.service.GmailPollingWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
public class IngestionSyncController {

    private final GmailPollingWorker gmailPollingWorker;

    @PostMapping("/sync")
    public ResponseEntity<IngestionSyncResponse> triggerSync(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader
    ) {
        log.info("Received manual ingestion sync request with X-User-Id: {}", userIdHeader);
        int discovered;
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                UUID userId = UUID.fromString(userIdHeader.trim());
                discovered = gmailPollingWorker.pollForUser(userId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid X-User-Id header value: {}", userIdHeader);
                return ResponseEntity.badRequest().body(
                        new IngestionSyncResponse("ERROR", 0, "Invalid X-User-Id format")
                );
            }
        } else {
            discovered = gmailPollingWorker.pollAllUsers();
        }

        return ResponseEntity.ok(
                new IngestionSyncResponse("SUCCESS", discovered, "Ingestion sync triggered successfully")
        );
    }
}
