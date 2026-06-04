package com.jobtrackr.application_service.kafka.event;

import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusUpdatedEvent {

    @Builder.Default
    private UUID eventId = UUID.randomUUID();

    private UUID applicationId;
    private UUID userId;
    private String companyName;
    private ApplicationStatus previousStatus;
    private ApplicationStatus newStatus;
    private String note;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
