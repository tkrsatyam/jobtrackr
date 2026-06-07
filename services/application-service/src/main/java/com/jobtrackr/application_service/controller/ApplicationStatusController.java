package com.jobtrackr.application_service.controller;

import com.jobtrackr.application_service.dto.request.ChangeStatusRequest;
import com.jobtrackr.application_service.dto.response.ApplicationResponse;
import com.jobtrackr.application_service.service.ApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications/{id}/status")
@RequiredArgsConstructor
public class ApplicationStatusController {

    private final ApplicationService applicationService;

    @PutMapping
    public ResponseEntity<ApplicationResponse> changeStatus(
            @PathVariable UUID id, @RequestBody ChangeStatusRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(applicationService.changeStatus(id, request, httpRequest));
    }

    @GetMapping
    public ResponseEntity<List<ApplicationResponse.StatusHistoryEntry>> getStatusHistory(@PathVariable UUID id,
                                                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.ok(applicationService.getStatusHistory(id, httpRequest));
    }
}
