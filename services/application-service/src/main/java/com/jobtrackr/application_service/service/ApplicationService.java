package com.jobtrackr.application_service.service;

import com.jobtrackr.application_service.dto.request.*;
import com.jobtrackr.application_service.dto.response.ApplicationResponse;
import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import com.jobtrackr.application_service.entity.enums.PriorityLevel;
import com.jobtrackr.application_service.entity.enums.WorkMode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ApplicationService {

    ApplicationResponse createApplication(CreateApplicationRequest request, HttpServletRequest httpRequest);

    ApplicationResponse getApplicationById(UUID applicationId, HttpServletRequest httpRequest);

    Page<ApplicationResponse> getAllApplications(ApplicationFilterRequest filter, HttpServletRequest httpRequest);

    ApplicationResponse updateApplication(UUID applicationId, UpdateApplicationRequest request,
                                          HttpServletRequest httpRequest);

    void deleteApplication(UUID applicationId, HttpServletRequest httpRequest);

    ApplicationResponse archiveApplication(UUID applicationId, HttpServletRequest httpRequest);

    ApplicationResponse changeStatus(UUID applicationId, ChangeStatusRequest request, HttpServletRequest httpRequest);

    List<ApplicationResponse.StatusHistoryEntry> getStatusHistory(UUID applicationId, HttpServletRequest httpRequest);

    ApplicationResponse addTag(UUID applicationId, String tag, HttpServletRequest httpRequest);

    ApplicationResponse removeTag(UUID applicationId, String tag, HttpServletRequest httpRequest);

    void bulkDelete(BulkActionRequest request, HttpServletRequest httpRequest);

    void bulkArchive(BulkActionRequest request, HttpServletRequest httpRequest);

    void bulkChangeStatus(BulkActionRequest request, HttpServletRequest httpRequest);
}