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

    /**
     * Single-row fetch used by every "verify ownership then act on one application" path
     * (get by id, update, delete, archive, change status, add/remove tag, status history).
     *
     * NOTE: intentionally does NOT use @EntityGraph to fetch-join both `tags` and
     * `statusHistory` in one query - both are List-typed ("bag") collections, and
     * fetch-joining two bags in a single query throws Hibernate's
     * MultipleBagFetchException regardless of pagination. Fixing that properly would mean
     * converting one of them to a Set (semantics/ordering change) or issuing two separate
     * fetch-joined queries - not worth it here, since this method only ever returns a
     * single row: letting `tags`/`statusHistory` lazy-load on access costs at most two
     * small extra queries, not an N+1. The @BatchSize on those collections still applies
     * if a future caller loads several Applications in one session/request.
     */
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
