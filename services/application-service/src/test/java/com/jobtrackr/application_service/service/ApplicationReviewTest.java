package com.jobtrackr.application_service.service;

import com.jobtrackr.application_service.dto.request.ConfirmReviewRequest;
import com.jobtrackr.application_service.dto.response.ApplicationResponse;
import com.jobtrackr.application_service.dto.response.PendingReviewCountResponse;
import com.jobtrackr.application_service.entity.Application;
import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import com.jobtrackr.application_service.kafka.producer.ApplicationEventProducer;
import com.jobtrackr.application_service.repository.ApplicationRepository;
import com.jobtrackr.application_service.repository.StatusHistoryRepository;
import com.jobtrackr.application_service.util.UserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationReviewTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private StatusHistoryRepository statusHistoryRepository;

    @Spy
    private ApplicationMapper applicationMapper = new ApplicationMapper();

    @Mock
    private ApplicationEventProducer eventProducer;

    @Mock
    private StatusTransitionValidator transitionValidator;

    @Mock
    private UserContextHolder userContextHolder;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private ApplicationServiceImpl applicationService;

    private UUID userId;
    private UUID appId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        appId = UUID.randomUUID();
        when(userContextHolder.getUserId(httpRequest)).thenReturn(userId);
    }

    @Test
    @DisplayName("Should retrieve pending review applications for user")
    void testGetPendingReviewApplications() {
        Application app = Application.builder()
                .applicationId(appId)
                .userId(userId)
                .companyName("Amazon")
                .role("SDE II")
                .status(ApplicationStatus.APPLIED)
                .requiresReview(true)
                .aiDiscovered(true)
                .statusHistory(new ArrayList<>())
                .tags(new ArrayList<>())
                .isDeleted(false)
                .build();

        when(applicationRepository.findByUserIdAndRequiresReviewTrueAndIsDeletedFalseOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(app));

        List<ApplicationResponse> responses = applicationService.getPendingReviewApplications(httpRequest);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCompanyName()).isEqualTo("Amazon");
        assertThat(responses.get(0).getRequiresReview()).isTrue();
    }

    @Test
    @DisplayName("Should return count of pending review applications")
    void testGetPendingReviewCount() {
        when(applicationRepository.countByUserIdAndRequiresReviewTrueAndIsDeletedFalse(userId))
                .thenReturn(3L);

        PendingReviewCountResponse response = applicationService.getPendingReviewCount(httpRequest);

        assertThat(response.count()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should confirm review, update fields, clear review flag, and notify event producer")
    void testConfirmReview() {
        Application app = Application.builder()
                .applicationId(appId)
                .userId(userId)
                .companyName("Uber")
                .role("Software Engineer")
                .status(ApplicationStatus.APPLIED)
                .requiresReview(true)
                .aiDiscovered(true)
                .statusHistory(new ArrayList<>())
                .tags(new ArrayList<>())
                .isDeleted(false)
                .build();

        when(applicationRepository.findByApplicationIdAndIsDeletedFalse(appId))
                .thenReturn(Optional.of(app));
        when(applicationRepository.save(any(Application.class))).thenAnswer(i -> i.getArgument(0));

        ConfirmReviewRequest confirmRequest = ConfirmReviewRequest.builder()
                .companyName("Uber Technologies")
                .role("Senior Software Engineer")
                .status(ApplicationStatus.APPLIED)
                .build();

        ApplicationResponse confirmed = applicationService.confirmReview(appId, confirmRequest, httpRequest);

        assertThat(confirmed.getCompanyName()).isEqualTo("Uber Technologies");
        assertThat(confirmed.getRole()).isEqualTo("Senior Software Engineer");
        assertThat(confirmed.getRequiresReview()).isFalse();

        verify(eventProducer).sendApplicationCreated(any());
    }

    @Test
    @DisplayName("Should reject review by marking application as deleted")
    void testRejectReview() {
        Application app = Application.builder()
                .applicationId(appId)
                .userId(userId)
                .companyName("Spam Corp")
                .role("Spam Role")
                .status(ApplicationStatus.APPLIED)
                .requiresReview(true)
                .aiDiscovered(true)
                .isDeleted(false)
                .build();

        when(applicationRepository.findByApplicationIdAndIsDeletedFalse(appId))
                .thenReturn(Optional.of(app));

        applicationService.rejectReview(appId, httpRequest);

        assertThat(app.getIsDeleted()).isTrue();
        assertThat(app.getRequiresReview()).isFalse();
        verify(applicationRepository).save(app);
    }
}
