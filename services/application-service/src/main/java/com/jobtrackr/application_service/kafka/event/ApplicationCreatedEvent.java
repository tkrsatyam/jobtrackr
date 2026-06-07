package com.jobtrackr.application_service.kafka.event;

import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationCreatedEvent {

    @Builder.Default
    private UUID eventId = UUID.randomUUID();

    private UUID applicationId;
    private UUID userId;
    private String companyName;
    private String role;
    private ApplicationStatus status;
    private LocalDate appliedDate;

    @Builder.Default
    private Instant timestamp = Instant.now();

}
