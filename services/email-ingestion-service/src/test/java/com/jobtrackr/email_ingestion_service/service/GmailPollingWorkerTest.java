package com.jobtrackr.email_ingestion_service.service;

import com.jobtrackr.email_ingestion_service.client.GmailClient;
import com.jobtrackr.email_ingestion_service.client.UserServiceClient;
import com.jobtrackr.email_ingestion_service.dto.JobApplicationExtractionRecord;
import com.jobtrackr.email_ingestion_service.exception.AiExtractionException;
import com.jobtrackr.email_ingestion_service.dto.UserGoogleAccountDTO;
import com.jobtrackr.email_ingestion_service.dto.UserGoogleTokenDTO;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailHeader;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailMessageDetail;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailMessageRef;
import com.jobtrackr.email_ingestion_service.dto.gmail.GmailPayload;
import com.jobtrackr.email_ingestion_service.entity.EmailPollingState;
import com.jobtrackr.email_ingestion_service.entity.EmailProcessingStatus;
import com.jobtrackr.email_ingestion_service.entity.ProcessedEmail;
import com.jobtrackr.email_ingestion_service.kafka.event.ApplicationDiscoveredEvent;
import com.jobtrackr.email_ingestion_service.kafka.producer.IngestionEventProducer;
import com.jobtrackr.email_ingestion_service.repository.EmailPollingStateRepository;
import com.jobtrackr.email_ingestion_service.repository.ProcessedEmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GmailPollingWorkerTest {

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private GmailClient gmailClient;

    @Mock
    private JobApplicationExtractor jobApplicationExtractor;

    @Mock
    private IngestionEventProducer ingestionEventProducer;

    @Mock
    private EmailPollingStateRepository emailPollingStateRepository;

    @Mock
    private ProcessedEmailRepository processedEmailRepository;

    @InjectMocks
    private GmailPollingWorker worker;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should process message, publish event and record state when job application extracted")
    void testPollForUserSuccess() {
        String token = "mock-access-token";
        when(userServiceClient.getGoogleToken(userId))
                .thenReturn(new UserGoogleTokenDTO(userId, "test@example.com", token, Instant.now().plusSeconds(3600)));

        when(emailPollingStateRepository.findById(userId))
                .thenReturn(Optional.of(EmailPollingState.builder().userId(userId).build()));

        GmailMessageRef msgRef = new GmailMessageRef("msg-101", "thread-101");
        when(gmailClient.listMessages(eq(token), anyString(), eq(25)))
                .thenReturn(List.of(msgRef));

        when(processedEmailRepository.existsByUserIdAndMessageId(userId, "msg-101"))
                .thenReturn(false);

        List<GmailHeader> headers = List.of(
                new GmailHeader("Subject", "Thank you for applying to Google"),
                new GmailHeader("From", "careers@google.com")
        );
        GmailPayload payload = new GmailPayload("text/plain", headers, null, null);
        GmailMessageDetail detail = new GmailMessageDetail("msg-101", "thread-101", "12345", "Snippet text", 1700000000000L, payload);
        when(gmailClient.getMessage(token, "msg-101"))
                .thenReturn(Optional.of(detail));

        JobApplicationExtractionRecord record = new JobApplicationExtractionRecord(
                true, "Google", "Software Engineer", "APPLIED",
                "Mountain View", null, null, "careers@google.com", "Applied for SE", 0.95
        );
        when(jobApplicationExtractor.extract(any(), any(), any()))
                .thenReturn(Optional.of(record));

        int discovered = worker.pollForUser(userId);

        assertThat(discovered).isEqualTo(1);

        verify(ingestionEventProducer, times(1)).publishApplicationDiscovered(any(ApplicationDiscoveredEvent.class));

        ArgumentCaptor<ProcessedEmail> emailCaptor = ArgumentCaptor.forClass(ProcessedEmail.class);
        verify(processedEmailRepository).save(emailCaptor.capture());
        assertThat(emailCaptor.getValue().getStatus()).isEqualTo(EmailProcessingStatus.PROCESSED);
        assertThat(emailCaptor.getValue().getMessageId()).isEqualTo("msg-101");

        verify(emailPollingStateRepository).save(any(EmailPollingState.class));
    }

    @Test
    @DisplayName("Should mark email as SKIPPED and not publish event when email is not job related")
    void testPollForUserSkipped() {
        String token = "mock-access-token";
        when(userServiceClient.getGoogleToken(userId))
                .thenReturn(new UserGoogleTokenDTO(userId, "test@example.com", token, Instant.now().plusSeconds(3600)));

        when(emailPollingStateRepository.findById(userId))
                .thenReturn(Optional.of(EmailPollingState.builder().userId(userId).build()));

        GmailMessageRef msgRef = new GmailMessageRef("msg-102", "thread-102");
        when(gmailClient.listMessages(eq(token), anyString(), eq(25)))
                .thenReturn(List.of(msgRef));

        when(processedEmailRepository.existsByUserIdAndMessageId(userId, "msg-102"))
                .thenReturn(false);

        GmailMessageDetail detail = new GmailMessageDetail("msg-102", "thread-102", "12346", "Newsletter", 1700000000000L, null);
        when(gmailClient.getMessage(token, "msg-102"))
                .thenReturn(Optional.of(detail));

        when(jobApplicationExtractor.extract(any(), any(), any()))
                .thenReturn(Optional.empty());

        int discovered = worker.pollForUser(userId);

        assertThat(discovered).isEqualTo(0);
        verify(ingestionEventProducer, never()).publishApplicationDiscovered(any());

        ArgumentCaptor<ProcessedEmail> emailCaptor = ArgumentCaptor.forClass(ProcessedEmail.class);
        verify(processedEmailRepository).save(emailCaptor.capture());
        assertThat(emailCaptor.getValue().getStatus()).isEqualTo(EmailProcessingStatus.SKIPPED);
    }

    @Test
    @DisplayName("Should poll for all users when pollAllUsers called")
    void testPollAllUsers() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        when(userServiceClient.getGoogleAccounts()).thenReturn(List.of(
                new UserGoogleAccountDTO(u1, "u1@example.com", "sub-1"),
                new UserGoogleAccountDTO(u2, "u2@example.com", "sub-2")
        ));

        when(userServiceClient.getGoogleToken(any()))
                .thenReturn(new UserGoogleTokenDTO(UUID.randomUUID(), "u@example.com", "token", Instant.now().plusSeconds(3600)));

        when(emailPollingStateRepository.findById(any()))
                .thenReturn(Optional.empty());

        when(gmailClient.listMessages(any(), any(), anyInt()))
                .thenReturn(List.of());

        int total = worker.pollAllUsers();

        assertThat(total).isEqualTo(0);
        verify(gmailClient, times(2)).listMessages(any(), any(), anyInt());
    }

    @Test
    @DisplayName("Should save email as FAILED with bodyText and lastError when AI extraction throws AiExtractionException")
    void testPollForUserAiFailureSavesAsFailed() {
        String token = "mock-access-token";
        when(userServiceClient.getGoogleToken(userId))
                .thenReturn(new UserGoogleTokenDTO(userId, "test@example.com", token, Instant.now().plusSeconds(3600)));

        when(emailPollingStateRepository.findById(userId))
                .thenReturn(Optional.of(EmailPollingState.builder().userId(userId).build()));

        GmailMessageRef msgRef = new GmailMessageRef("msg-error", "thread-error");
        when(gmailClient.listMessages(eq(token), anyString(), eq(25)))
                .thenReturn(List.of(msgRef));

        when(processedEmailRepository.existsByUserIdAndMessageId(userId, "msg-error"))
                .thenReturn(false);

        List<GmailHeader> headers = List.of(
                new GmailHeader("Subject", "Application for SDE at Amazon"),
                new GmailHeader("From", "careers@amazon.com")
        );
        GmailPayload payload = new GmailPayload("text/plain", headers, null, null);
        GmailMessageDetail detail = new GmailMessageDetail("msg-error", "thread-error", "12345", "Snippet text", 1700000000000L, payload);
        when(gmailClient.getMessage(token, "msg-error"))
                .thenReturn(Optional.of(detail));

        when(jobApplicationExtractor.extract(any(), any(), any()))
                .thenThrow(new AiExtractionException("Gemini 503 Service Unavailable"));

        int discovered = worker.pollForUser(userId);

        assertThat(discovered).isEqualTo(0);
        ArgumentCaptor<ProcessedEmail> captor = ArgumentCaptor.forClass(ProcessedEmail.class);
        verify(processedEmailRepository).save(captor.capture());
        ProcessedEmail saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(EmailProcessingStatus.FAILED);
        assertThat(saved.getMessageId()).isEqualTo("msg-error");
        assertThat(saved.getRetryCount()).isEqualTo(0);
        assertThat(saved.getLastError()).contains("Gemini 503 Service Unavailable");
        verify(ingestionEventProducer, never()).publishApplicationDiscovered(any());
    }

    @Test
    @DisplayName("Should retry failed emails, publish event, and update status to PROCESSED when extraction succeeds")
    void testPollForUserRetriesFailedEmailsSuccessfully() {
        String token = "mock-access-token";
        when(userServiceClient.getGoogleToken(userId))
                .thenReturn(new UserGoogleTokenDTO(userId, "test@example.com", token, Instant.now().plusSeconds(3600)));

        when(emailPollingStateRepository.findById(userId))
                .thenReturn(Optional.of(EmailPollingState.builder().userId(userId).build()));

        when(gmailClient.listMessages(eq(token), anyString(), eq(25)))
                .thenReturn(List.of());

        ProcessedEmail failedEmail = ProcessedEmail.builder()
                .userId(userId)
                .messageId("msg-failed-1")
                .subject("Interview at Stripe")
                .sender("recruiting@stripe.com")
                .bodyText("We would like to invite you for an interview.")
                .receivedAt(Instant.now().minusSeconds(3600))
                .status(EmailProcessingStatus.FAILED)
                .retryCount(1)
                .lastError("Previous 429 quota error")
                .build();

        when(processedEmailRepository.findByUserIdAndStatusAndRetryCountLessThanOrderByReceivedAtAsc(eq(userId), eq(EmailProcessingStatus.FAILED), eq(3)))
                .thenReturn(List.of(failedEmail));

        JobApplicationExtractionRecord record = new JobApplicationExtractionRecord(
                true, "Stripe", "Backend Engineer", "INTERVIEW",
                "Remote", null, null, "recruiting@stripe.com", "Interview invite", 0.98
        );
        when(jobApplicationExtractor.extract("Interview at Stripe", "recruiting@stripe.com", "We would like to invite you for an interview."))
                .thenReturn(Optional.of(record));

        int discovered = worker.pollForUser(userId);

        assertThat(discovered).isEqualTo(1);
        verify(ingestionEventProducer, times(1)).publishApplicationDiscovered(any(ApplicationDiscoveredEvent.class));

        ArgumentCaptor<ProcessedEmail> captor = ArgumentCaptor.forClass(ProcessedEmail.class);
        verify(processedEmailRepository).save(captor.capture());
        ProcessedEmail updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo(EmailProcessingStatus.PROCESSED);
        assertThat(updated.getBodyText()).isNull();
        assertThat(updated.getLastError()).isNull();
    }

    @Test
    @DisplayName("Should increment retryCount when retry encounters AiExtractionException")
    void testPollForUserRetryFailsAndIncrementsRetryCount() {
        String token = "mock-access-token";
        when(userServiceClient.getGoogleToken(userId))
                .thenReturn(new UserGoogleTokenDTO(userId, "test@example.com", token, Instant.now().plusSeconds(3600)));

        when(emailPollingStateRepository.findById(userId))
                .thenReturn(Optional.of(EmailPollingState.builder().userId(userId).build()));

        when(gmailClient.listMessages(eq(token), anyString(), eq(25)))
                .thenReturn(List.of());

        ProcessedEmail failedEmail = ProcessedEmail.builder()
                .userId(userId)
                .messageId("msg-failed-2")
                .subject("Interview at Stripe")
                .sender("recruiting@stripe.com")
                .bodyText("Interview details.")
                .receivedAt(Instant.now().minusSeconds(3600))
                .status(EmailProcessingStatus.FAILED)
                .retryCount(1)
                .lastError("First 429 error")
                .build();

        when(processedEmailRepository.findByUserIdAndStatusAndRetryCountLessThanOrderByReceivedAtAsc(eq(userId), eq(EmailProcessingStatus.FAILED), eq(3)))
                .thenReturn(List.of(failedEmail));

        when(jobApplicationExtractor.extract(any(), any(), any()))
                .thenThrow(new AiExtractionException("Still 429 quota exhausted"));

        int discovered = worker.pollForUser(userId);

        assertThat(discovered).isEqualTo(0);
        verify(ingestionEventProducer, never()).publishApplicationDiscovered(any());

        ArgumentCaptor<ProcessedEmail> captor = ArgumentCaptor.forClass(ProcessedEmail.class);
        verify(processedEmailRepository).save(captor.capture());
        ProcessedEmail updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo(EmailProcessingStatus.FAILED);
        assertThat(updated.getRetryCount()).isEqualTo(2);
        assertThat(updated.getLastError()).contains("Still 429 quota exhausted");
    }
}
