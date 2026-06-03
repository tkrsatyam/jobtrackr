package com.jobtrackr.application_service.dto.request;

import com.jobtrackr.application_service.entity.enums.ApplicationSource;
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
public class UpdateApplicationRequest {
    private String companyName;
    private String role;
    private String jobUrl;
    private PriorityLevel priority;
    private WorkMode workMode;
    private String location;
    private Long salaryMin;
    private Long salaryMax;
    private String currency;
    private LocalDate appliedDate;
    private ApplicationSource source;
    private String notes;
}
