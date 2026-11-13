package com.jobtrackr.email_ingestion_service.repository;

import com.jobtrackr.email_ingestion_service.dto.StatusCountDTO;
import com.jobtrackr.email_ingestion_service.entity.EmailProcessingStatus;
import com.jobtrackr.email_ingestion_service.entity.ProcessedEmail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessedEmailRepository extends JpaRepository<ProcessedEmail, UUID> {

    boolean existsByUserIdAndMessageId(UUID userId, String messageId);

    boolean existsByUserIdAndMessageIdAndStatus(UUID userId, String messageId, EmailProcessingStatus status);

    Optional<ProcessedEmail> findByUserIdAndMessageId(UUID userId, String messageId);

    List<ProcessedEmail> findByUserIdAndStatusAndRetryCountLessThanOrderByReceivedAtAsc(
            UUID userId,
            EmailProcessingStatus status,
            int maxRetries,
            Pageable pageable
    );

    Page<ProcessedEmail> findByUserIdOrderByReceivedAtDesc(UUID userId, Pageable pageable);

    Page<ProcessedEmail> findByUserIdAndStatusOrderByReceivedAtDesc(
            UUID userId,
            EmailProcessingStatus status,
            Pageable pageable
    );

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, EmailProcessingStatus status);

    @Query("""
            SELECT new com.jobtrackr.email_ingestion_service.dto.StatusCountDTO(p.status, COUNT(p))
            FROM ProcessedEmail p
            WHERE p.userId = :userId
            GROUP BY p.status
            """)
    List<StatusCountDTO> countGroupedByStatus(@Param("userId") UUID userId);
}
