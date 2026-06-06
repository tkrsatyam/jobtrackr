package com.jobtrackr.application_service.controller;

import com.jobtrackr.application_service.dto.request.ApplicationFilterRequest;
import com.jobtrackr.application_service.dto.request.CreateApplicationRequest;
import com.jobtrackr.application_service.dto.request.UpdateApplicationRequest;
import com.jobtrackr.application_service.dto.response.ApplicationResponse;
import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import com.jobtrackr.application_service.entity.enums.PriorityLevel;
import com.jobtrackr.application_service.entity.enums.WorkMode;
import com.jobtrackr.application_service.service.ApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    public ResponseEntity<ApplicationResponse> createApplication(@Valid @RequestBody CreateApplicationRequest request,
                                                                 HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.createApplication(request, httpRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getApplicationById(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(applicationService.getApplicationById(id, httpRequest));
    }

    @GetMapping
    public ResponseEntity<Page<ApplicationResponse>> getAllApplications(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) PriorityLevel priority,
            @RequestParam(required = false) WorkMode workMode,
            @RequestParam(required = false) Boolean isArchived,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appliedAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appliedBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            HttpServletRequest httpRequest) {

        ApplicationFilterRequest filter = ApplicationFilterRequest.builder()
                .status(status)
                .priority(priority)
                .workMode(workMode)
                .isArchived(isArchived)
                .company(company)
                .role(role)
                .appliedAfter(appliedAfter)
                .appliedBefore(appliedBefore)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDir(sortDir)
                .build();

        return ResponseEntity.ok(applicationService.getAllApplications(filter, httpRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApplicationResponse> updateApplication(@PathVariable UUID id, UpdateApplicationRequest request,
                                                                 HttpServletRequest httpRequest) {
        return ResponseEntity.ok(applicationService.updateApplication(id, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable UUID id, HttpServletRequest httpServlet) {
        applicationService.deleteApplication(id, httpServlet);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<ApplicationResponse> archiveApplication(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(applicationService.archiveApplication(id, httpRequest));
    }
}
