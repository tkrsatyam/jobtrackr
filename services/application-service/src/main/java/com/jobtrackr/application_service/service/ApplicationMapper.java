package com.jobtrackr.application_service.service;

import com.jobtrackr.application_service.dto.request.CreateApplicationRequest;
import com.jobtrackr.application_service.dto.request.UpdateApplicationRequest;
import com.jobtrackr.application_service.dto.response.ApplicationResponse;
import com.jobtrackr.application_service.entity.Application;
import com.jobtrackr.application_service.entity.ApplicationStatusHistory;
import com.jobtrackr.application_service.entity.ApplicationTag;
import com.jobtrackr.application_service.entity.enums.ApplicationSource;
import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import com.jobtrackr.application_service.entity.enums.PriorityLevel;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class ApplicationMapper {

    public Application toEntity(CreateApplicationRequest request, UUID userId) {
        Application application = Application.builder()
                .userId(userId)
                .companyName(request.getCompanyName())
                .role(request.getRole())
                .jobUrl(request.getJobUrl())
                .status(request.getStatus() != null ? request.getStatus() : ApplicationStatus.APPLIED)
                .priority(request.getPriority() != null ? request.getPriority() : PriorityLevel.MEDIUM)
                .workMode(request.getWorkMode())
                .location(request.getLocation())
                .salaryMin(request.getSalaryMin())
                .salaryMax(request.getSalaryMax())
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .appliedDate(request.getAppliedDate())
                .source(request.getSource() != null ? request.getSource() : ApplicationSource.OTHER)
                .notes(request.getNotes())
                .build();

        if (request.getTags() != null) {
            List<ApplicationTag> tagEntities = request.getTags().stream()
                    .filter(tag -> tag != null && !tag.isBlank())
                    .map(tag -> ApplicationTag.builder()
                            .application(application)
                            .tag(tag.trim().toLowerCase())
                            .build())
                    .toList();
            application.getTags().addAll(tagEntities);
        }

        return application;
    }

    public ApplicationResponse toResponse(Application application) {
        List<String> tags = application.getTags() == null
                ? Collections.emptyList()
                : application.getTags().stream()
                        .map(ApplicationTag::getTag)
                        .toList();

        List<ApplicationResponse.StatusHistoryEntry> history = application.getStatusHistory() == null
                ? Collections.emptyList()
                : application.getStatusHistory().stream()
                .map(h -> ApplicationResponse.StatusHistoryEntry.builder()
                        .status(h.getStatus())
                        .note(h.getNote())
                        .changedAt(h.getChangedAt())
                        .build())
                        .toList();

        return ApplicationResponse.builder()
                .applicationId(application.getApplicationId())
                .userId(application.getUserId())
                .companyName(application.getCompanyName())
                .role(application.getRole())
                .jobUrl(application.getJobUrl())
                .status(application.getStatus())
                .priority(application.getPriority())
                .workMode(application.getWorkMode())
                .location(application.getLocation())
                .salaryMin(application.getSalaryMin())
                .salaryMax(application.getSalaryMax())
                .currency(application.getCurrency())
                .appliedDate(application.getAppliedDate())
                .source(application.getSource())
                .notes(application.getNotes())
                .isArchived(application.getIsArchived())
                .isDeleted(application.getIsDeleted())
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .tags(tags)
                .statusHistory(history)
                .build();
    }

    public void updateEntity(Application application, UpdateApplicationRequest request) {
        if (request.getCompanyName() != null) application.setCompanyName(request.getCompanyName());
        if (request.getRole() != null) application.setRole(request.getRole());
        if (request.getJobUrl() != null) application.setJobUrl(request.getJobUrl());
        if (request.getPriority() != null) application.setPriority(request.getPriority());
        if (request.getWorkMode() != null) application.setWorkMode(request.getWorkMode());
        if (request.getLocation() != null) application.setLocation(request.getLocation());
        if (request.getSalaryMin() != null) application.setSalaryMin(request.getSalaryMin());
        if (request.getSalaryMax() != null) application.setSalaryMax(request.getSalaryMax());
        if (request.getCurrency() != null) application.setCurrency(request.getCurrency());
        if (request.getAppliedDate() != null) application.setAppliedDate(request.getAppliedDate());
        if (request.getSource() != null) application.setSource(request.getSource());
        if (request.getNotes() != null) application.setNotes(request.getNotes());
    }

    public ApplicationStatusHistory toHistoryEntity(Application application, ApplicationStatus status, String note) {
        return ApplicationStatusHistory.builder()
                .application(application)
                .status(status)
                .note(note)
                .build();
    }
}