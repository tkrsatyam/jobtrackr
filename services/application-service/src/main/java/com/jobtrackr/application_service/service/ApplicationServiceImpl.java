package com.jobtrackr.application_service.service;

import com.jobtrackr.application_service.dto.request.*;
import com.jobtrackr.application_service.dto.response.ApplicationResponse;
import com.jobtrackr.application_service.dto.response.ApplicationSearchResult;
import com.jobtrackr.application_service.dto.response.PendingReviewCountResponse;
import com.jobtrackr.application_service.entity.Application;
import com.jobtrackr.application_service.entity.ApplicationStatusHistory;
import com.jobtrackr.application_service.entity.ApplicationTag;
import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import com.jobtrackr.application_service.exception.ApplicationNotFoundException;
import com.jobtrackr.application_service.exception.UnauthorizedAccessException;
import com.jobtrackr.application_service.kafka.event.ApplicationCreatedEvent;
import com.jobtrackr.application_service.kafka.event.ApplicationDeletedEvent;
import com.jobtrackr.application_service.kafka.event.StatusUpdatedEvent;
import com.jobtrackr.application_service.kafka.producer.ApplicationEventProducer;
import com.jobtrackr.application_service.repository.ApplicationRepository;
import com.jobtrackr.application_service.repository.StatusHistoryRepository;
import com.jobtrackr.application_service.util.ApplicationSpecification;
import com.jobtrackr.application_service.util.PaginationUtils;
import com.jobtrackr.application_service.util.UserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final ApplicationMapper applicationMapper;
    private final ApplicationEventProducer eventProducer;
    private final StatusTransitionValidator transitionValidator;
    private final UserContextHolder userContextHolder;
    
    private static final int MAX_TAG_FILTERS = 10;

    @Override
    public ApplicationResponse createApplication(CreateApplicationRequest request, HttpServletRequest httpRequest) {

        UUID userId = userContextHolder.getUserId(httpRequest);
        Application application = applicationMapper.toEntity(request, userId);

        ApplicationStatusHistory initialHistory = applicationMapper.toHistoryEntity(
                application, application.getStatus(), "Application created");
        application.getStatusHistory().add(initialHistory);

        application = applicationRepository.save(application);
        eventProducer.sendApplicationCreated(ApplicationCreatedEvent.builder()
                .applicationId(application.getApplicationId())
                .userId(application.getUserId())
                .companyName(application.getCompanyName())
                .role(application.getRole())
                .status(application.getStatus())
                .appliedDate(application.getAppliedDate())
                .build());

        return applicationMapper.toResponse(application);
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationResponse getApplicationById(UUID applicationId, HttpServletRequest httpRequest) {

        UUID userId = userContextHolder.getUserId(httpRequest);
        Application application = findAndVerifyOwnership(applicationId, userId);

        return applicationMapper.toResponse(application);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ApplicationResponse> getAllApplications(ApplicationFilterRequest filter, HttpServletRequest httpRequest) {

        UUID userId = userContextHolder.getUserId(httpRequest);
        Pageable pageable = PaginationUtils.buildPageable(
                filter.getPage(), filter.getSize(), filter.getSortBy(), filter.getSortDir());

        Specification<Application> spec = Specification
                .where(ApplicationSpecification.forUser(userId))
                .and(ApplicationSpecification.notDeleted());

        if (filter.getRequiresReview() != null) {
            spec = spec.and(ApplicationSpecification.requiresReview(filter.getRequiresReview()));
        } else {
            spec = spec.and(ApplicationSpecification.requiresReview(false));
        }

        if (filter.getStatus() != null) spec = spec.and(ApplicationSpecification.hasStatus(filter.getStatus()));
        if (filter.getPriority() != null) spec = spec.and(ApplicationSpecification.hasPriority(filter.getPriority()));
        if (filter.getWorkMode() != null) spec = spec.and(ApplicationSpecification.hasWorkMode(filter.getWorkMode()));
        if (filter.getIsArchived() != null) spec = spec.and(ApplicationSpecification.isArchived(filter.getIsArchived()));
        if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) 
            spec = spec.and(ApplicationSpecification.keywordMatches(filter.getKeyword()));
        if (filter.getCompany() != null) spec = spec.and(ApplicationSpecification.companyContains(filter.getCompany()));
        if (filter.getRole() != null) spec = spec.and(ApplicationSpecification.roleContains(filter.getRole()));
        if (filter.getAppliedAfter() != null) spec = spec.and(ApplicationSpecification.appliedAfter(filter.getAppliedAfter()));
        if (filter.getAppliedBefore() != null) spec = spec.and(ApplicationSpecification.appliedBefore(filter.getAppliedBefore()));
        
        if (filter.getTags() != null && !filter.getTags().isEmpty()) {
            List<String> effectiveTags = filter.getTags().stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(t -> t.trim().toLowerCase())
                    .collect(Collectors.toCollection(LinkedHashSet::new))
                    .stream().toList();
            log.info("Effective tags: {}", effectiveTags);
            if (!effectiveTags.isEmpty()) {
                if (effectiveTags.size() > MAX_TAG_FILTERS) {
                    throw new IllegalArgumentException("Cannot filter by more than " + MAX_TAG_FILTERS + " tags");
                }
                for (String tag: effectiveTags) {
                    spec = spec.and(ApplicationSpecification.hasTag(tag));
                }
            }
        }

        return applicationRepository.findAll(spec, pageable)
                .map(applicationMapper::toResponse);
    }

    @Override
    public ApplicationResponse updateApplication(UUID applicationId, UpdateApplicationRequest request, HttpServletRequest httpRequest) {

        UUID userId = userContextHolder.getUserId(httpRequest);

        Application application =  findAndVerifyOwnership(applicationId, userId);
        applicationMapper.updateEntity(application, request);

        return applicationMapper.toResponse(applicationRepository.save(application));
    }

    @Override
    public void deleteApplication(UUID applicationId, HttpServletRequest httpRequest) {

        UUID userId = userContextHolder.getUserId(httpRequest);

        Application application = findAndVerifyOwnership(applicationId, userId);
        application.setIsDeleted(true);
        applicationRepository.save(application);

        eventProducer.sendApplicationDeleted(ApplicationDeletedEvent.builder()
                .applicationId(applicationId)
                .userId(userId)
                .build());
    }

    @Override
    public ApplicationResponse archiveApplication(UUID applicationId, HttpServletRequest httpRequest) {

        UUID userId = userContextHolder.getUserId(httpRequest);

        Application application =  findAndVerifyOwnership(applicationId, userId);
        application.setIsArchived(!application.getIsArchived());

        return applicationMapper.toResponse(applicationRepository.save(application));
    }

    @Override
    public ApplicationResponse changeStatus(UUID applicationId, ChangeStatusRequest request,
                                            HttpServletRequest httpRequest) {

        UUID userId = userContextHolder.getUserId(httpRequest);
        Application application = findAndVerifyOwnership(applicationId, userId);

        ApplicationStatus previousStatus = application.getStatus();
        transitionValidator.validate(previousStatus, request.getStatus());
        application.setStatus(request.getStatus());

        ApplicationStatusHistory history = applicationMapper.toHistoryEntity(
                application, request.getStatus(), request.getNote());
        application.getStatusHistory().add(history);

        application = applicationRepository.save(application);
        eventProducer.sendStatusUpdated(StatusUpdatedEvent.builder()
                .applicationId(applicationId)
                .userId(userId)
                .companyName(application.getCompanyName())
                .previousStatus(previousStatus)
                .newStatus(application.getStatus())
                .note(request.getNote())
                .build());

        return applicationMapper.toResponse(application);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationResponse.StatusHistoryEntry> getStatusHistory(UUID applicationId,
                                                                         HttpServletRequest httpRequest) {
        UUID userId = userContextHolder.getUserId(httpRequest);
        findAndVerifyOwnership(applicationId, userId);

        return statusHistoryRepository.findByApplicationApplicationIdOrderByChangedAtDesc(applicationId)
                .stream()
                .map(history -> ApplicationResponse.StatusHistoryEntry.builder()
                        .status(history.getStatus())
                        .note(history.getNote())
                        .changedAt(history.getChangedAt())
                        .build())
                .toList();
    }

    @Override
    public ApplicationResponse addTag(UUID applicationId, String tag, HttpServletRequest httpRequest) {

        UUID userId = userContextHolder.getUserId(httpRequest);
        Application application = findAndVerifyOwnership(applicationId, userId);

        String normalizedTag = tag.trim().toLowerCase();
        boolean alreadyExists = application.getTags().stream()
                .anyMatch(t -> t.getTag().equals(normalizedTag));

        if (!alreadyExists) {
            ApplicationTag newTag = ApplicationTag.builder()
                    .application(application)
                    .tag(normalizedTag)
                    .build();
            application.getTags().add(newTag);
            application = applicationRepository.save(application);
        }

        return applicationMapper.toResponse(application);
    }

    @Override
    public ApplicationResponse removeTag(UUID applicationId, String tag, HttpServletRequest httpRequest) {

        UUID userId = userContextHolder.getUserId(httpRequest);
        Application application = findAndVerifyOwnership(applicationId, userId);

        String normalizedTag = tag.trim().toLowerCase();
        application.getTags().removeIf(t -> t.getTag().equals(normalizedTag));

        return applicationMapper.toResponse(applicationRepository.save(application));
    }

    @Override
    public void bulkDelete(BulkActionRequest request, HttpServletRequest httpRequest) {

        UUID userId = userContextHolder.getUserId(httpRequest);
        List<Application> applications = applicationRepository.findAllById(request.getIds())
                .stream()
                .filter(application -> application.getUserId().equals(userId) && !application.getIsDeleted())
                .toList();

        applications.forEach(application -> {
            application.setIsDeleted(true);
            eventProducer.sendApplicationDeleted(ApplicationDeletedEvent.builder()
                    .applicationId(application.getApplicationId())
                    .userId(userId)
                    .build());
        });

        applicationRepository.saveAll(applications);
    }

    @Override
    public void bulkArchive(BulkActionRequest request, HttpServletRequest httpRequest) {

        UUID userId = userContextHolder.getUserId(httpRequest);
        List<Application> applications = applicationRepository.findAllById(request.getIds())
                .stream()
                .filter(application -> application.getUserId().equals(userId)
                        && !application.getIsDeleted()
                        && !application.getIsArchived())
                .toList();

        applications.forEach(application -> application.setIsArchived(true));
        applicationRepository.saveAll(applications);
    }

    @Override
    public void bulkUnarchive(BulkActionRequest request, HttpServletRequest httpRequest) {

        UUID userId = userContextHolder.getUserId(httpRequest);
        List<Application> applications = applicationRepository.findAllById(request.getIds())
                .stream()
                .filter(application -> application.getUserId().equals(userId)
                        && !application.getIsDeleted()
                        && application.getIsArchived())
                .toList();

        applications.forEach(application -> application.setIsArchived(false));
        applicationRepository.saveAll(applications);
    }

    @Override
    public void bulkChangeStatus(BulkActionRequest request, HttpServletRequest httpRequest) {

        UUID userId = userContextHolder.getUserId(httpRequest);
        if (request.getStatus() == null) {
            throw new IllegalArgumentException("Status is required for bulk status change");
        }

        List<Application> applications = applicationRepository.findAllById(request.getIds())
                .stream()
                .filter(application -> application.getUserId().equals(userId) && !application.getIsDeleted())
                .toList();

        applications.forEach(application -> {
            ApplicationStatus previousStatus = application.getStatus();
            try {
                transitionValidator.validate(previousStatus, request.getStatus());
                application.setStatus(request.getStatus());

                ApplicationStatusHistory history = applicationMapper.toHistoryEntity(
                        application, request.getStatus(), "Bulk status change");
                application.getStatusHistory().add(history);

                eventProducer.sendStatusUpdated(StatusUpdatedEvent.builder()
                        .applicationId(application.getApplicationId())
                        .userId(userId)
                        .companyName(application.getCompanyName())
                        .previousStatus(previousStatus)
                        .newStatus(request.getStatus())
                        .note("Bulk status change")
                        .build());
            } catch (Exception e) {
                //Skip invalid status change silently
            }
        });

        applicationRepository.saveAll(applications);
    }

    @Override
    public void bulkAddTag(BulkActionRequest request, HttpServletRequest httpRequest) {

        UUID userId = userContextHolder.getUserId(httpRequest);
        if (request.getTags() == null || request.getTags().isEmpty()) {
            throw new IllegalArgumentException("At least one tag is required for bulk tag add");
        }

        List<String> normalizedTags = request.getTags().stream()
                .map(t -> t.trim().toLowerCase())
                .filter(t -> !t.isBlank())
                .distinct()
                .toList();

        List<Application> applications = applicationRepository.findAllById(request.getIds())
                .stream()
                .filter(application -> application.getUserId().equals(userId) && !application.getIsDeleted())
                .toList();

        applications.forEach(application ->
                normalizedTags.forEach(normalizedTag -> {
                    boolean alreadyExists = application.getTags().stream()
                            .anyMatch(t -> t.getTag().equals(normalizedTag));

                    if (!alreadyExists) {
                        ApplicationTag newTag = ApplicationTag.builder()
                                .application(application)
                                .tag(normalizedTag)
                                .build();
                        application.getTags().add(newTag);
                    }
                })
        );

        applicationRepository.saveAll(applications);
    }

    @Override
    public void bulkRemoveTag(BulkActionRequest request, HttpServletRequest httpRequest) {

        UUID userId = userContextHolder.getUserId(httpRequest);
        if (request.getTags() == null || request.getTags().isEmpty()) {
            throw new IllegalArgumentException("At least one tag is required for bulk tag remove");
        }

        List<String> normalizedTags = request.getTags().stream()
                .map(t -> t.trim().toLowerCase())
                .filter(t -> !t.isBlank())
                .distinct()
                .toList();

        List<Application> applications = applicationRepository.findAllById(request.getIds())
                .stream()
                .filter(application -> application.getUserId().equals(userId) && !application.getIsDeleted())
                .toList();

        applications.forEach(application ->
                application.getTags().removeIf(t -> normalizedTags.contains(t.getTag())));

        applicationRepository.saveAll(applications);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationSearchResult> searchApplications(String keyword, int size, HttpServletRequest httpRequest) {
        UUID userId = userContextHolder.getUserId(httpRequest);
        String pattern = "%" + keyword.toLowerCase() + "%";
        return applicationRepository.searchByKeyword(userId, pattern, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationResponse> getPendingReviewApplications(HttpServletRequest httpRequest) {
        UUID userId = userContextHolder.getUserId(httpRequest);
        return applicationRepository.findByUserIdAndRequiresReviewTrueAndIsDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(applicationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PendingReviewCountResponse getPendingReviewCount(HttpServletRequest httpRequest) {
        UUID userId = userContextHolder.getUserId(httpRequest);
        long count = applicationRepository.countByUserIdAndRequiresReviewTrueAndIsDeletedFalse(userId);
        return new PendingReviewCountResponse(count);
    }

    @Override
    public ApplicationResponse confirmReview(UUID applicationId, ConfirmReviewRequest request, HttpServletRequest httpRequest) {
        UUID userId = userContextHolder.getUserId(httpRequest);
        Application application = findAndVerifyOwnership(applicationId, userId);

        if (request != null) {
            if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
                application.setCompanyName(request.getCompanyName().trim());
            }
            if (request.getRole() != null && !request.getRole().isBlank()) {
                application.setRole(request.getRole().trim());
            }
            if (request.getLocation() != null) {
                application.setLocation(request.getLocation());
            }
            if (request.getJobUrl() != null) {
                application.setJobUrl(request.getJobUrl());
            }
            if (request.getPriority() != null) {
                application.setPriority(request.getPriority());
            }
            if (request.getWorkMode() != null) {
                application.setWorkMode(request.getWorkMode());
            }
            if (request.getAppliedDate() != null) {
                application.setAppliedDate(request.getAppliedDate());
            }
            if (request.getNotes() != null) {
                application.setNotes(request.getNotes());
            }
            if (request.getSource() != null) {
                application.setSource(request.getSource());
            }
            if (request.getStatus() != null && request.getStatus() != application.getStatus()) {
                ApplicationStatus oldStatus = application.getStatus();
                application.setStatus(request.getStatus());
                ApplicationStatusHistory history = applicationMapper.toHistoryEntity(
                        application, request.getStatus(), "Confirmed review with status change from " + oldStatus
                );
                application.getStatusHistory().add(history);
            }
        }

        application.setRequiresReview(false);
        application = applicationRepository.save(application);

        eventProducer.sendApplicationCreated(ApplicationCreatedEvent.builder()
                .applicationId(application.getApplicationId())
                .userId(application.getUserId())
                .companyName(application.getCompanyName())
                .role(application.getRole())
                .status(application.getStatus())
                .appliedDate(application.getAppliedDate())
                .build());

        return applicationMapper.toResponse(application);
    }

    @Override
    public void rejectReview(UUID applicationId, HttpServletRequest httpRequest) {
        UUID userId = userContextHolder.getUserId(httpRequest);
        Application application = findAndVerifyOwnership(applicationId, userId);
        application.setIsDeleted(true);
        application.setRequiresReview(false);
        applicationRepository.save(application);

        if (application.getEmailMessageId() != null) {
            eventProducer.sendApplicationReviewRejected(userId, application.getEmailMessageId());
        }
    }

    @Override
    public ApplicationResponse acceptStatusProposal(UUID applicationId, HttpServletRequest httpRequest) {
        UUID userId = userContextHolder.getUserId(httpRequest);
        Application application = findAndVerifyOwnership(applicationId, userId);

        if (application.getProposedStatus() == null) {
            throw new IllegalStateException("No status change is currently proposed for this application");
        }

        ApplicationStatus oldStatus = application.getStatus();
        ApplicationStatus newStatus = application.getProposedStatus();
        String reason = application.getStatusProposalReason();

        transitionValidator.validate(oldStatus, newStatus);

        application.setStatus(newStatus);
        application.setProposedStatus(null);
        application.setStatusProposalReason(null);
        application.setStatusProposalReceivedAt(null);

        String historyNote = reason != null && !reason.isBlank()
                ? "Status updated from email proposal: " + reason
                : "Status updated from email proposal";

        ApplicationStatusHistory history = applicationMapper.toHistoryEntity(application, newStatus, historyNote);
        application.getStatusHistory().add(history);

        application = applicationRepository.save(application);
        eventProducer.sendStatusUpdated(StatusUpdatedEvent.builder()
                .applicationId(application.getApplicationId())
                .userId(userId)
                .companyName(application.getCompanyName())
                .previousStatus(oldStatus)
                .newStatus(newStatus)
                .note(historyNote)
                .build());

        return applicationMapper.toResponse(application);
    }

    @Override
    public ApplicationResponse dismissStatusProposal(UUID applicationId, HttpServletRequest httpRequest) {
        UUID userId = userContextHolder.getUserId(httpRequest);
        Application application = findAndVerifyOwnership(applicationId, userId);

        application.setProposedStatus(null);
        application.setStatusProposalReason(null);
        application.setStatusProposalReceivedAt(null);

        application = applicationRepository.save(application);
        return applicationMapper.toResponse(application);
    }

    private Application findAndVerifyOwnership(UUID applicationId, UUID userId) {
        Application application = applicationRepository
                .findByApplicationIdAndIsDeletedFalse(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        if (!application.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("You do not have access to this application");
        }

        return application;
    }
}
