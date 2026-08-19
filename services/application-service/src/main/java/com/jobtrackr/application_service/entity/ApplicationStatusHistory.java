package com.jobtrackr.application_service.entity;

import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "application_status_history", indexes = {
        @Index(name = "idx_status_history_app", columnList = "application_id")
})
@Getter
@Setter
@ToString(exclude = {"application"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStatusHistory {

    @Id
    @UuidGenerator
    private UUID historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Builder.Default
    private LocalDateTime changedAt = LocalDateTime.now();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApplicationStatusHistory other)) return false;
        return historyId != null && historyId.equals(other.historyId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
