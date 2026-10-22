package com.jobtrackr.application_service.entity;

import com.jobtrackr.application_service.entity.enums.ApplicationSource;
import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import com.jobtrackr.application_service.entity.enums.PriorityLevel;
import com.jobtrackr.application_service.entity.enums.WorkMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "applications", indexes = {
        @Index(name = "idx_applications_user", columnList = "user_id"),
        @Index(name = "idx_applications_status", columnList = "status"),
        @Index(name = "idx_applications_user_status", columnList = "user_id, status"),
        @Index(name = "idx_applications_user_review", columnList = "user_id, requires_review"),
        @Index(name = "idx_applications_email_msg", columnList = "user_id, email_message_id")
})
@Getter
@Setter
@ToString(exclude = {"statusHistory", "tags", "documents"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Application {

    @Id
    @UuidGenerator
    private UUID applicationId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String companyName;

    @Column(nullable = false, length = 200)
    private String role;

    @Column(length = 1000)
    private String jobUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Enumerated(EnumType.STRING)
    private PriorityLevel priority;

    @Enumerated(EnumType.STRING)
    private WorkMode workMode;

    @Column(length = 200)
    private String location;

    private Long salaryMin;
    private Long salaryMax;

    @Column(length = 10)
    private String currency;

    private LocalDate appliedDate;

    @Enumerated(EnumType.STRING)
    private ApplicationSource source;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isArchived = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isDeleted = false;

    @Builder.Default
    @Column(name = "ai_discovered", nullable = false)
    private Boolean aiDiscovered = false;

    @Builder.Default
    @Column(name = "requires_review", nullable = false)
    private Boolean requiresReview = false;

    @Column(name = "confidence_score", precision = 3, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "raw_email_snippet", columnDefinition = "TEXT")
    private String rawEmailSnippet;

    @Column(name = "email_message_id", length = 255)
    private String emailMessageId;

    @Column(name = "email_received_at")
    private Instant emailReceivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposed_status")
    private ApplicationStatus proposedStatus;

    @Column(name = "status_proposal_reason", length = 500)
    private String statusProposalReason;

    @Column(name = "status_proposal_received_at")
    private Instant statusProposalReceivedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder.Default
    @BatchSize(size = 20)
    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ApplicationStatusHistory> statusHistory = new ArrayList<>();

    @Builder.Default
    @BatchSize(size = 20)
    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ApplicationTag> tags = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ApplicationDocument> documents = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Application other)) return false;
        return applicationId != null && applicationId.equals(other.applicationId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
