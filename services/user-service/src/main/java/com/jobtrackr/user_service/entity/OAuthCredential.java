package com.jobtrackr.user_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "oauth_credentials",
        uniqueConstraints = @UniqueConstraint(name = "uk_oauth_credentials_user_provider", columnNames = {"user_id", "provider"}),
        indexes = @Index(name = "idx_oauth_credentials_user_id", columnList = "user_id"))
@Getter
@Setter
@ToString(exclude = {"user", "accessToken", "refreshToken"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthCredential {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private User.AuthProvider provider;

    @Column(name = "provider_user_id")
    private String providerUserId;

    private String email;

    @Column(columnDefinition = "TEXT")
    private String accessToken;

    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    private Instant expiresAt;

    @Column(columnDefinition = "TEXT")
    private String scopes;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OAuthCredential that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
