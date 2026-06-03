package com.jobtrackr.application_service.repository;

import com.jobtrackr.application_service.entity.Application;
import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID>, JpaSpecificationExecutor<Application> {

    List<Application> findByUserIdAndIsDeletedFalse(UUID userId);

    List<Application> findByUserIdAndStatusAndIsDeletedFalse(UUID userId, ApplicationStatus status);

    Page<Application> findByUserIdAndIsDeletedFalse(UUID userId, Pageable pageable);

    Optional<Application> findByApplicationIdAndIsDeletedFalse(UUID applicationId);

    Page<Application> findAll(Specification<Application> spec, Pageable pageable);
}
