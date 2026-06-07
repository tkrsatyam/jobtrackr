package com.jobtrackr.application_service.repository;

import com.jobtrackr.application_service.entity.ApplicationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, UUID> {

    List<ApplicationStatusHistory> findByApplicationApplicationIdOrderByChangedAtDesc(UUID applicationId);
}
