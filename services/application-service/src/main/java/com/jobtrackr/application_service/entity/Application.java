package com.jobtrackr.application_service.entity;

import com.jobtrackr.application_service.entity.enums.ApplicationSource;
import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import com.jobtrackr.application_service.entity.enums.PriorityLevel;
import com.jobtrackr.application_service.entity.enums.WorkMode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "applications")
@Data
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

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ApplicationStatusHistory> statusHistory = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ApplicationTag> tags = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ApplicationDocument> documents = new ArrayList<>();
}
