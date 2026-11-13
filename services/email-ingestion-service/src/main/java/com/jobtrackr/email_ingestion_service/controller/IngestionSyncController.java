package com.jobtrackr.email_ingestion_service.controller;

import com.jobtrackr.email_ingestion_service.dto.IngestionStatusResponse;
import com.jobtrackr.email_ingestion_service.dto.IngestionSyncResponse;
import com.jobtrackr.email_ingestion_service.dto.PageResponse;
import com.jobtrackr.email_ingestion_service.dto.ProcessedEmailResponse;
import com.jobtrackr.email_ingestion_service.entity.EmailProcessingStatus;
import com.jobtrackr.email_ingestion_service.service.EmailIngestionQueryService;
import com.jobtrackr.email_ingestion_service.service.GmailPollingWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
public class IngestionSyncController {

    private final GmailPollingWorker gmailPollingWorker;
    private final EmailIngestionQueryService queryService;

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

    @GetMapping("/status")
    public ResponseEntity<IngestionStatusResponse> getStatus(
            @RequestHeader("X-User-Id") String userIdHeader
    ) {
        UUID userId = UUID.fromString(userIdHeader.trim());
        IngestionStatusResponse status = queryService.getStatus(userId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/history")
    public ResponseEntity<PageResponse<ProcessedEmailResponse>> getHistory(
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestParam(value = "status", required = false) String statusParam,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        UUID userId = UUID.fromString(userIdHeader.trim());
        EmailProcessingStatus status = null;
        if (statusParam != null && !statusParam.isBlank() && !statusParam.equalsIgnoreCase("ALL")) {
            try {
                status = EmailProcessingStatus.valueOf(statusParam.toUpperCase().trim());
            } catch (IllegalArgumentException ignored) {}
        }

        Page<ProcessedEmailResponse> history = queryService.getHistory(userId, status, PageRequest.of(page, size));
        return ResponseEntity.ok(PageResponse.from(history));
    }

    @PostMapping("/emails/{messageId}/re-ingest")
    public ResponseEntity<ProcessedEmailResponse> reIngest(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable("messageId") String messageId
    ) {
        UUID userId = UUID.fromString(userIdHeader.trim());
        ProcessedEmailResponse response = queryService.reIngestEmail(userId, messageId);
        return ResponseEntity.ok(response);
    }
}
