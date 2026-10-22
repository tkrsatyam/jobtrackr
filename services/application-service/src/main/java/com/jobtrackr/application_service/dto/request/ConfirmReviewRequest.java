package com.jobtrackr.application_service.dto.request;

import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import com.jobtrackr.application_service.entity.enums.PriorityLevel;
import com.jobtrackr.application_service.entity.enums.WorkMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmReviewRequest {
    private String companyName;
    private String role;
    private ApplicationStatus status;
    private String location;
    private String jobUrl;
    private PriorityLevel priority;
    private WorkMode workMode;
    private LocalDate appliedDate;
    private String notes;
}
