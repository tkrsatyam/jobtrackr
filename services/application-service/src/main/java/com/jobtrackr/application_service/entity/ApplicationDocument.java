package com.jobtrackr.application_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "application_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDocument {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(nullable = false)
    private UUID documentId;

    @Builder.Default
    private LocalDateTime linkedAt = LocalDateTime.now();
}
