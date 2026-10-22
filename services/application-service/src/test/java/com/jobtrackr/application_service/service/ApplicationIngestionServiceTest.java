package com.jobtrackr.application_service.service;

import com.jobtrackr.application_service.entity.Application;
import com.jobtrackr.application_service.entity.enums.ApplicationSource;
import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import com.jobtrackr.application_service.kafka.event.ApplicationDiscoveredEvent;
import com.jobtrackr.application_service.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationIngestionServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @InjectMocks
    private ApplicationIngestionService ingestionService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should skip event if emailMessageId already ingested")
    void testSkipDuplicateMessageId() {
        when(applicationRepository.existsByUserIdAndEmailMessageId(userId, "msg-123")).thenReturn(true);

        ApplicationDiscoveredEvent event = new ApplicationDiscoveredEvent(
                UUID.randomUUID(), userId, "Google", "SWE", "APPLIED",
                "Remote", null, null, null, "Applied", 0.9,
                "msg-123", "Subject", "from@google.com", Instant.now()
        );

        ingestionService.processDiscoveredApplication(event);

        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update existing application notes if duplicate role at company within 30 days")
    void testDeduplicateExistingApplication() {
        when(applicationRepository.existsByUserIdAndEmailMessageId(userId, "msg-456")).thenReturn(false);

        Application existing = Application.builder()
                .applicationId(UUID.randomUUID())
                .userId(userId)
                .companyName("Stripe")
                .role("Backend Engineer")
                .status(ApplicationStatus.APPLIED)
                .appliedDate(LocalDate.now().minusDays(5))
                .notes("Existing application note")
                .build();

        when(applicationRepository.findPotentialDuplicates(eq(userId), eq("Stripe"), eq("Backend Engineer"), any()))
                .thenReturn(List.of(existing));

        ApplicationDiscoveredEvent event = new ApplicationDiscoveredEvent(
                UUID.randomUUID(), userId, "Stripe", "Backend Engineer", "INTERVIEW",
                "SF", null, null, null, "Invitation to interview", 0.95,
                "msg-456", "Interview Invitation", "recruiting@stripe.com", Instant.now()
        );

        ingestionService.processDiscoveredApplication(event);

        verify(applicationRepository).save(existing);
        assertThat(existing.getNotes()).contains("Existing application note");
        assertThat(existing.getNotes()).contains("[Email update");
        assertThat(existing.getNotes()).contains("Invitation to interview");
    }

    @Test
    @DisplayName("Should create new application with requiresReview=true when no duplicate exists")
    void testCreateNewApplicationAwaitingReview() {
        when(applicationRepository.existsByUserIdAndEmailMessageId(userId, "msg-789")).thenReturn(false);
        when(applicationRepository.findPotentialDuplicates(eq(userId), eq("Netflix"), eq("Senior Engineer"), any()))
                .thenReturn(Collections.emptyList());

        ApplicationDiscoveredEvent event = new ApplicationDiscoveredEvent(
                UUID.randomUUID(), userId, "Netflix", "Senior Engineer", "APPLIED",
                "Los Gatos", "https://netflix.jobs/123", "Jane Doe", "jane@netflix.com",
                "Thank you for applying", 0.92,
                "msg-789", "Thanks for applying to Netflix", "jobs@netflix.com", Instant.now()
        );

        ingestionService.processDiscoveredApplication(event);

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(captor.capture());

        Application saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getCompanyName()).isEqualTo("Netflix");
        assertThat(saved.getRole()).isEqualTo("Senior Engineer");
        assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(saved.getAiDiscovered()).isTrue();
        assertThat(saved.getRequiresReview()).isTrue();
        assertThat(saved.getConfidenceScore()).isNotNull();
        assertThat(saved.getSource()).isEqualTo(ApplicationSource.EMAIL);
        assertThat(saved.getEmailMessageId()).isEqualTo("msg-789");
    }
}
