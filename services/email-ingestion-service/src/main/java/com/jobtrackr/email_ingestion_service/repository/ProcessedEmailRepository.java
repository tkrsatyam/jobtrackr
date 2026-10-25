package com.jobtrackr.email_ingestion_service.repository;

import com.jobtrackr.email_ingestion_service.entity.EmailProcessingStatus;
import com.jobtrackr.email_ingestion_service.entity.ProcessedEmail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
