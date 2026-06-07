package com.jobtrackr.application_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "document-service", fallback = DocumentServiceClientFallback.class)
public interface DocumentServiceClient {

    @GetMapping("/api/documents/application/{applicationId}")
    List<Map<String, Object>> getLinkedDocuments(
            @PathVariable("applicationId") UUID applicationId,
            @RequestHeader("X-User-Id") String userId
    );
}
