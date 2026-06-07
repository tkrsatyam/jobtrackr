package com.jobtrackr.application_service.dto.response;

import com.jobtrackr.application_service.entity.enums.ApplicationSource;
import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import com.jobtrackr.application_service.entity.enums.PriorityLevel;
import com.jobtrackr.application_service.entity.enums.WorkMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationResponse {

    private UUID applicationId;
    private UUID userId;
    private String companyName;
    private String role;
    private String jobUrl;
    private ApplicationStatus status;
    private PriorityLevel priority;
    private WorkMode workMode;
    private String location;
    private Long salaryMin;
    private Long salaryMax;
    private String currency;
    private LocalDate appliedDate;
    private ApplicationSource source;
    private String notes;
    private Boolean isArchived;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<String> tags;
    private List<StatusHistoryEntry> statusHistory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistoryEntry {
        private ApplicationStatus status;
        private String note;
        private LocalDateTime changedAt;
    }
}
