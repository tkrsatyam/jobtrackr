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

import java.math.BigDecimal;
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
    @DisplayName("Should update existing application notes if duplicate role at company within 14 days")
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
                .requiresReview(false)
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
    @DisplayName("Should upgrade confidence score and enrich jobUrl when incoming unreviewed event has higher confidence")
    void testUpgradeConfidenceAndEnrichFieldsWhenInReview() {
        when(applicationRepository.existsByUserIdAndEmailMessageId(userId, "msg-novo-direct")).thenReturn(false);

        Application existing = Application.builder()
                .applicationId(UUID.randomUUID())
                .userId(userId)
                .companyName("Novo Tree Minds")
                .role("Java Developer")
                .status(ApplicationStatus.APPLIED)
                .appliedDate(LocalDate.now().minusDays(1))
                .confidenceScore(new BigDecimal("0.80"))
                .location("Pune, Maharashtra, India 411001")
                .requiresReview(true)
                .notes("Discovered from Indeed")
                .build();

        when(applicationRepository.findPotentialDuplicates(eq(userId), eq("Novo Tree Minds"), eq("Java Developer"), any()))
                .thenReturn(List.of(existing));

        ApplicationDiscoveredEvent higherConfidenceEvent = new ApplicationDiscoveredEvent(
                UUID.randomUUID(), userId, "Novo Tree Minds", "Java Developer", "APPLIED",
                null, "https://recooty.com/jobs/view/998877", null, "notifications@recooty.com",
                "Direct confirmation from Recooty", 0.85,
                "msg-novo-direct", "Application Received - Thank You!", "notifications@recooty.com", Instant.now()
        );

        ingestionService.processDiscoveredApplication(higherConfidenceEvent);

        verify(applicationRepository).save(existing);
        assertThat(existing.getConfidenceScore()).isEqualTo(new BigDecimal("0.85"));
        assertThat(existing.getJobUrl()).isEqualTo("https://recooty.com/jobs/view/998877");
        assertThat(existing.getLocation()).isEqualTo("Pune, Maharashtra, India 411001");
        assertThat(existing.getNotes()).contains("Discovered from Indeed");
        assertThat(existing.getNotes()).contains("[Email update");
        assertThat(existing.getNotes()).contains("Direct confirmation from Recooty");
    }

    @Test
    @DisplayName("Should retain higher confidence score but enrich missing location when incoming event has lower confidence")
    void testRetainHigherConfidenceAndEnrichMissingLocation() {
        when(applicationRepository.existsByUserIdAndEmailMessageId(userId, "msg-indeed-second")).thenReturn(false);

        Application existing = Application.builder()
                .applicationId(UUID.randomUUID())
                .userId(userId)
                .companyName("Novo Tree Minds")
                .role("Java Developer")
                .status(ApplicationStatus.APPLIED)
                .appliedDate(LocalDate.now().minusDays(1))
                .confidenceScore(new BigDecimal("0.85"))
                .jobUrl("https://recooty.com/jobs/view/998877")
                .requiresReview(true)
                .notes("Direct Recooty note")
                .build();

        when(applicationRepository.findPotentialDuplicates(eq(userId), eq("Novo Tree Minds"), eq("Java Developer"), any()))
                .thenReturn(List.of(existing));

        ApplicationDiscoveredEvent lowerConfidenceEvent = new ApplicationDiscoveredEvent(
                UUID.randomUUID(), userId, "Novo Tree Minds", "Java Developer", "APPLIED",
                "Pune, Maharashtra, India 411001", null, null, "indeedapply@indeed.com",
                "Indeed submission", 0.80,
                "msg-indeed-second", "Indeed Application: Java Developer", "indeedapply@indeed.com", Instant.now()
        );

        ingestionService.processDiscoveredApplication(lowerConfidenceEvent);

        verify(applicationRepository).save(existing);
        assertThat(existing.getConfidenceScore()).isEqualTo(new BigDecimal("0.85"));
        assertThat(existing.getLocation()).isEqualTo("Pune, Maharashtra, India 411001");
        assertThat(existing.getJobUrl()).isEqualTo("https://recooty.com/jobs/view/998877");
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

    @Test
    @DisplayName("Should match single active application by company when role is generic Applicant Position")
    void testSingleActiveCompanyFallbackForGenericRole() {
        when(applicationRepository.existsByUserIdAndEmailMessageId(userId, "msg-stripe-invite")).thenReturn(false);
        when(applicationRepository.findPotentialDuplicates(eq(userId), eq("Stripe"), eq("Applicant Position"), any()))
                .thenReturn(Collections.emptyList());

        Application activeApp = Application.builder()
                .applicationId(UUID.randomUUID())
                .userId(userId)
                .companyName("Stripe")
                .role("Backend Engineer")
                .status(ApplicationStatus.APPLIED)
                .appliedDate(LocalDate.now().minusDays(20))
                .requiresReview(false)
                .build();

        when(applicationRepository.findActiveByCompany(eq(userId), eq("Stripe"), any()))
                .thenReturn(List.of(activeApp));

        ApplicationDiscoveredEvent event = new ApplicationDiscoveredEvent(
                UUID.randomUUID(), userId, "Stripe", "Applicant Position", "INTERVIEW",
                null, null, null, null, "Please schedule interview", 0.90,
                "msg-stripe-invite", "Update regarding your application at Stripe", "jobs@stripe.com", Instant.now()
        );

        ingestionService.processDiscoveredApplication(event);

        verify(applicationRepository).save(activeApp);
        assertThat(activeApp.getProposedStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(activeApp.getStatusProposalReason()).contains("Please schedule interview");
    }

    @Test
    @DisplayName("Should not auto-match when multiple active applications exist for company with generic role")
    void testMultipleActiveCompanyFallbackDoesNotAutoMatch() {
        when(applicationRepository.existsByUserIdAndEmailMessageId(userId, "msg-multi-stripe")).thenReturn(false);
        when(applicationRepository.findPotentialDuplicates(eq(userId), eq("Stripe"), eq("Applicant Position"), any()))
                .thenReturn(Collections.emptyList());

        Application app1 = Application.builder()
                .applicationId(UUID.randomUUID())
                .userId(userId)
                .companyName("Stripe")
                .role("Backend Engineer")
                .status(ApplicationStatus.APPLIED)
                .appliedDate(LocalDate.now().minusDays(10))
                .requiresReview(false)
                .build();

        Application app2 = Application.builder()
                .applicationId(UUID.randomUUID())
                .userId(userId)
                .companyName("Stripe")
                .role("Full Stack Engineer")
                .status(ApplicationStatus.APPLIED)
                .appliedDate(LocalDate.now().minusDays(15))
                .requiresReview(false)
                .build();

        when(applicationRepository.findActiveByCompany(eq(userId), eq("Stripe"), any()))
                .thenReturn(List.of(app1, app2));

        ApplicationDiscoveredEvent event = new ApplicationDiscoveredEvent(
                UUID.randomUUID(), userId, "Stripe", "Applicant Position", "INTERVIEW",
                null, null, null, null, "Interview invitation", 0.90,
                "msg-multi-stripe", "Stripe Interview", "jobs@stripe.com", Instant.now()
        );

        ingestionService.processDiscoveredApplication(event);

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(captor.capture());
        Application saved = captor.getValue();
        assertThat(saved.getRequiresReview()).isTrue();
        assertThat(saved.getCompanyName()).isEqualTo("Stripe");
    }

    @Test
    @DisplayName("Should upgrade source from generic EMAIL to specific INDEED upon duplicate merge")
    void testUpgradeSourceFromEmailToIndeedOnMerge() {
        when(applicationRepository.existsByUserIdAndEmailMessageId(userId, "msg-indeed")).thenReturn(false);

        Application existing = Application.builder()
                .applicationId(UUID.randomUUID())
                .userId(userId)
                .companyName("Novo Tree Minds")
                .role("Java Developer")
                .status(ApplicationStatus.APPLIED)
                .source(ApplicationSource.EMAIL)
                .appliedDate(LocalDate.now())
                .requiresReview(true)
                .build();

        when(applicationRepository.findPotentialDuplicates(eq(userId), eq("Novo Tree Minds"), eq("Java Developer"), any()))
                .thenReturn(List.of(existing));

        ApplicationDiscoveredEvent incomingIndeedEvent = new ApplicationDiscoveredEvent(
                UUID.randomUUID(), userId, "Novo Tree Minds", "Java Developer", "APPLIED",
                "Pune", "https://indeed.com/viewjob?id=123", null, null,
                "Submitted via Indeed", 0.95,
                "msg-indeed", "Indeed Application: Java Developer", "indeedapply@indeed.com", Instant.now()
        );

        ingestionService.processDiscoveredApplication(incomingIndeedEvent);

        verify(applicationRepository).save(existing);
        assertThat(existing.getSource()).isEqualTo(ApplicationSource.INDEED);
    }

    @Test
    @DisplayName("Should preserve multi-email snippets in ascending chronological order")
    void testMultiEmailSnippetsPreservedInAscendingOrder() {
        when(applicationRepository.existsByUserIdAndEmailMessageId(userId, "msg-earlier-application")).thenReturn(false);

        Instant laterTime = Instant.parse("2026-10-05T17:50:49Z");
        Instant earlierTime = Instant.parse("2026-10-05T10:50:49Z");

        // Existing application was created with the later email first
        Application existing = Application.builder()
                .applicationId(UUID.randomUUID())
                .userId(userId)
                .companyName("Novo Tree Minds")
                .role("Java Developer")
                .status(ApplicationStatus.APPLIED)
                .appliedDate(LocalDate.of(2026, 10, 5))
                .emailReceivedAt(laterTime)
                .rawEmailSnippet("=== [2026-10-05T17:50:49Z] Indeed Application: Java Developer (indeedapply@indeed.com) ===\nSubmitted via Indeed.")
                .requiresReview(true)
                .build();

        when(applicationRepository.findPotentialDuplicates(eq(userId), eq("Novo Tree Minds"), eq("Java Developer"), any()))
                .thenReturn(List.of(existing));

        // Now incoming event is an earlier email (e.g. from Recooty)
        ApplicationDiscoveredEvent earlierEvent = new ApplicationDiscoveredEvent(
                UUID.randomUUID(), userId, "Novo Tree Minds", "Java Developer", "APPLIED",
                null, null, null, null,
                "Application Received - Thank You!", 0.90,
                "msg-earlier-application", "Application Received - Thank You!", "notifications@recooty.com", earlierTime
        );

        ingestionService.processDiscoveredApplication(earlierEvent);

        verify(applicationRepository).save(existing);
        String snippet = existing.getRawEmailSnippet();
        assertThat(snippet).isNotNull();

        // The earlier email must appear BEFORE the later email
        int earlierIndex = snippet.indexOf("=== [2026-10-05T10:50:49Z]");
        int laterIndex = snippet.indexOf("=== [2026-10-05T17:50:49Z]");
        assertThat(earlierIndex).isGreaterThanOrEqualTo(0);
        assertThat(laterIndex).isGreaterThanOrEqualTo(0);
        assertThat(earlierIndex).isLessThan(laterIndex);
    }
}