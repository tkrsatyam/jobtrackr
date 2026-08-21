package com.jobtrackr.application_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(
        name = "application_tags",
        uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "tag"})
)
@Getter
@Setter
@ToString(exclude = {"application"})
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApplicationTag other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
