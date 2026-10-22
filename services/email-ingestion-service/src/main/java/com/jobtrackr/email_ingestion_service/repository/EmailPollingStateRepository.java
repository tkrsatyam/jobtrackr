package com.jobtrackr.email_ingestion_service.repository;

import com.jobtrackr.email_ingestion_service.entity.EmailPollingState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EmailPollingStateRepository extends JpaRepository<EmailPollingState, UUID> {
}
