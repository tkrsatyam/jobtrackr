package com.jobtrackr.application_service.util;

import com.jobtrackr.application_service.entity.Application;
import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import com.jobtrackr.application_service.entity.enums.PriorityLevel;
import com.jobtrackr.application_service.entity.enums.WorkMode;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

public class ApplicationSpecification {

    private ApplicationSpecification() {}

    public static Specification<Application> forUser(UUID userId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("userId"), userId);
    }

    public static Specification<Application> notDeleted() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isFalse(root.get("isDeleted"));
    }

    public static Specification<Application> hasStatus(ApplicationStatus status) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status);
    }

    public static Specification<Application> hasPriority(PriorityLevel priority) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("priority"), priority);
    }

    public static Specification<Application> hasWorkMode(WorkMode workMode) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("workMode"), workMode);
    }

    public static Specification<Application> isArchived(Boolean archived) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("isArchived"), archived);
    }

    public static Specification<Application> companyContains(String keyword) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("companyName")), "%" + keyword.toLowerCase() + "%");
    }

    public static Specification<Application> roleContains(String keyword) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("role")), "%" + keyword.toLowerCase() + "%");
    }

    public static Specification<Application> appliedAfter(LocalDate date) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("appliedDate"), date);
    }

    public static Specification<Application> appliedBefore(LocalDate date) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("appliedDate"), date);
    }
}
