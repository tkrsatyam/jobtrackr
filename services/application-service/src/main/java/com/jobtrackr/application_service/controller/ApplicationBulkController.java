package com.jobtrackr.application_service.controller;

import com.jobtrackr.application_service.dto.request.BulkActionRequest;
import com.jobtrackr.application_service.service.ApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications/bulk")
@RequiredArgsConstructor
public class ApplicationBulkController {

    private final ApplicationService applicationService;

    @PostMapping("/delete")
    public ResponseEntity<Void> bulkDelete(@Valid @RequestBody BulkActionRequest request, HttpServletRequest httpRequest) {
        applicationService.bulkDelete(request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/archive")
    public ResponseEntity<Void> bulkArchive(@Valid @RequestBody BulkActionRequest request, HttpServletRequest httpRequest) {
        applicationService.bulkArchive(request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/status")
    public ResponseEntity<Void> bulkChangeStatus(@Valid @RequestBody BulkActionRequest request, HttpServletRequest httpRequest) {
        applicationService.bulkChangeStatus(request, httpRequest);
        return ResponseEntity.noContent().build();
    }
}
