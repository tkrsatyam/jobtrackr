package com.jobtrackr.application_service.service;

import com.jobtrackr.application_service.dto.request.*;
import com.jobtrackr.application_service.dto.response.ApplicationResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService{

    private final ApplicationRepository applicationRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final ApplicationMapper applicationMapper;
    private final ApplicationEventProducer eventProducer;
    private final StatusTransitionValidator transitionValidator;
    private final UserContextHolder userContextHolder;

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

        if (filter.getStatus() != null) spec = spec.and(ApplicationSpecification.hasStatus(filter.getStatus()));
        if (filter.getPriority() != null) spec = spec.and(ApplicationSpecification.hasPriority(filter.getPriority()));
        if (filter.getWorkMode() != null) spec = spec.and(ApplicationSpecification.hasWorkMode(filter.getWorkMode()));
        if (filter.getIsArchived() != null) spec = spec.and(ApplicationSpecification.isArchived(filter.getIsArchived()));
        if (filter.getCompany() != null) spec = spec.and(ApplicationSpecification.companyContains(filter.getCompany()));
        if (filter.getRole() != null) spec = spec.and(ApplicationSpecification.roleContains(filter.getRole()));
        if (filter.getAppliedAfter() != null) spec = spec.and(ApplicationSpecification.appliedAfter(filter.getAppliedAfter()));
        if (filter.getAppliedBefore() != null) spec = spec.and(ApplicationSpecification.appliedBefore(filter.getAppliedBefore()));

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
                .filter(application -> application.getUserId().equals(userId) && !application.getIsDeleted())
                .toList();

        applications.forEach(application -> application.setIsArchived(true));
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