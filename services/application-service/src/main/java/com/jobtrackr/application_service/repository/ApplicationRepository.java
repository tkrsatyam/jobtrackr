package com.jobtrackr.application_service.repository;

import com.jobtrackr.application_service.dto.response.ApplicationSearchResult;
import com.jobtrackr.application_service.entity.Application;
import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID>, JpaSpecificationExecutor<Application> {

    List<Application> findByUserIdAndIsDeletedFalse(UUID userId);

    List<Application> findByUserIdAndStatusAndIsDeletedFalse(UUID userId, ApplicationStatus status);

    Page<Application> findByUserIdAndIsDeletedFalse(UUID userId, Pageable pageable);

    Optional<Application> findByApplicationIdAndIsDeletedFalse(UUID applicationId);

    Page<Application> findAll(Specification<Application> spec, Pageable pageable);
    
    @Query("""
            SELECT new com.jobtrackr.application_service.dto.response.ApplicationSearchResult(
                a.applicationId, a.companyName, a.role, a.status)
            FROM Application a
            WHERE a.userId = :userId
                AND a.isDeleted = false
                AND (lower(a.companyName) LIKE :pattern OR lower(a.role) LIKE :pattern)
            ORDER BY a.updatedAt DESC
            LIMIT :limit
            """)
    List<ApplicationSearchResult> searchByKeyword(@Param("userId") UUID userId,
                                                  @Param("pattern") String pattern,
                                                  @Param("limit") int limit);
}
