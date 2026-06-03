package com.jobtrackr.application_service.dto.request;

import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkActionRequest {

    @NotEmpty(message = "Application IDs must not be empty")
    private List<UUID> ids;

    // for bulk status change
    private ApplicationStatus status;
}
