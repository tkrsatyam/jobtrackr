package com.jobtrackr.application_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(
        name = "application_tags",
        uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "tag"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationTag {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(nullable = false, length = 50)
    private String tag;
}
