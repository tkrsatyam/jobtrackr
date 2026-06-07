package com.jobtrackr.application_service.kafka.event;

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
public class ApplicationDeletedEvent {

    @Builder.Default
    private UUID eventId = UUID.randomUUID();

    private UUID applicationId;
    private UUID userId;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
