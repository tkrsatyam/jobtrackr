package com.jobtrackr.email_ingestion_service.service;

import com.jobtrackr.email_ingestion_service.client.GmailClient;
import com.jobtrackr.email_ingestion_service.client.UserServiceClient;
import com.jobtrackr.email_ingestion_service.dto.IngestionStatusResponse;
import com.jobtrackr.email_ingestion_service.dto.StatusCountDTO;
import com.jobtrackr.email_ingestion_service.dto.UserGoogleTokenDTO;
import com.jobtrackr.email_ingestion_service.entity.EmailPollingState;
import com.jobtrackr.email_ingestion_service.entity.EmailProcessingStatus;
import com.jobtrackr.email_ingestion_service.kafka.producer.IngestionEventProducer;
import com.jobtrackr.email_ingestion_service.repository.EmailPollingStateRepository;
import com.jobtrackr.email_ingestion_service.repository.ProcessedEmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailIngestionQueryServiceTest {

    @Mock
    private ProcessedEmailRepository processedEmailRepository;

    @Mock
    private EmailPollingStateRepository emailPollingStateRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private GmailClient gmailClient;

    @Mock
    private JobApplicationExtractor jobApplicationExtractor;

    @Mock
    private IngestionEventProducer ingestionEventProducer;

    private EmailIngestionQueryService queryService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        queryService = new EmailIngestionQueryService(
                processedEmailRepository,
                emailPollingStateRepository,
                userServiceClient,
                gmailClient,
                jobApplicationExtractor,
                ingestionEventProducer
        );
    }

    @Test
    @DisplayName("Should aggregate status counts using single grouped query")
    void testGetStatusAggregatesCountsUsingGroupedQuery() {
        when(userServiceClient.getGoogleToken(userId))
                .thenReturn(new UserGoogleTokenDTO(userId, "dev@example.com", "token", Instant.now().plusSeconds(3600)));

        Instant lastPolled = Instant.now().minusSeconds(120);
        when(emailPollingStateRepository.findById(userId))
                .thenReturn(Optional.of(EmailPollingState.builder().userId(userId).lastPolledAt(lastPolled).build()));

        List<StatusCountDTO> groupedCounts = List.of(
                new StatusCountDTO(EmailProcessingStatus.PROCESSED, 15),
                new StatusCountDTO(EmailProcessingStatus.SKIPPED, 10),
                new StatusCountDTO(EmailProcessingStatus.REJECTED, 2),
                new StatusCountDTO(EmailProcessingStatus.FAILED, 1)
        );
        when(processedEmailRepository.countGroupedByStatus(userId)).thenReturn(groupedCounts);

        IngestionStatusResponse status = queryService.getStatus(userId);

        assertThat(status.connected()).isTrue();
        assertThat(status.connectedEmail()).isEqualTo("dev@example.com");
        assertThat(status.lastPolledAt()).isEqualTo(lastPolled);
        assertThat(status.totalScanned()).isEqualTo(28);
        assertThat(status.totalProcessed()).isEqualTo(15);
        assertThat(status.totalSkipped()).isEqualTo(10);
        assertThat(status.totalRejected()).isEqualTo(2);
        assertThat(status.totalFailed()).isEqualTo(1);
    }
}
