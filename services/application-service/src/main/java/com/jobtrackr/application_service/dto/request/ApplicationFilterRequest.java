package com.jobtrackr.application_service.dto.request;

import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import com.jobtrackr.application_service.entity.enums.PriorityLevel;
import com.jobtrackr.application_service.entity.enums.WorkMode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ApplicationFilterRequest {
    private ApplicationStatus status;
    private PriorityLevel priority;
    private WorkMode workMode;
    private Boolean isArchived;
    private String company;
    private String role;
    private LocalDate appliedAfter;
    private LocalDate appliedBefore;
    private int page;
    private int size;
    private String sortBy;
    private String sortDir;
}