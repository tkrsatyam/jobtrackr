package com.jobtrackr.application_service.dto.response;

import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSearchResult {
    private UUID applicationId;
    private String companyName;
    private String role;
    private ApplicationStatus status;
}
