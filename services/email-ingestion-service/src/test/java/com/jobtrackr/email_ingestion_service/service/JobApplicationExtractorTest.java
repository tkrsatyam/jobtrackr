package com.jobtrackr.email_ingestion_service.service;

import com.jobtrackr.email_ingestion_service.dto.JobApplicationExtractionRecord;
import com.jobtrackr.email_ingestion_service.exception.AiExtractionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobApplicationExtractorTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private JobApplicationExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new JobApplicationExtractor(chatClient);
    }

    private void mockChatResponse(String content) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(content);
    }

    @Test
    @DisplayName("Should extract application when AI returns valid application JSON")
    void shouldExtractApplicationWhenAiReturnsValidApplicationJson() {
        String json = """
                {
                  "isJobApplication": true,
                  "companyName": "Stripe",
                  "roleTitle": "Senior Backend Engineer",
                  "status": "APPLIED",
                  "location": "San Francisco, CA",
                  "jobUrl": "https://stripe.com/jobs/123",
                  "recruiterName": "Alex",
                  "recruiterEmail": "alex@stripe.com",
                  "notes": "Application confirmed for Senior Backend Engineer at Stripe.",
                  "confidenceScore": 0.95
                }
                """;
        mockChatResponse(json);

        Optional<JobApplicationExtractionRecord> result = extractor.extract(
                "Application for Senior Backend Engineer at Stripe",
                "jobs@stripe.com",
                "Thank you for your application."
        );

        assertThat(result).isPresent();
        JobApplicationExtractionRecord rec = result.get();
        assertThat(rec.isJobApplication()).isTrue();
        assertThat(rec.companyName()).isEqualTo("Stripe");
        assertThat(rec.roleTitle()).isEqualTo("Senior Backend Engineer");
        assertThat(rec.status()).isEqualTo("APPLIED");
        assertThat(rec.location()).isEqualTo("San Francisco, CA");
        assertThat(rec.jobUrl()).isEqualTo("https://stripe.com/jobs/123");
        assertThat(rec.recruiterName()).isEqualTo("Alex");
        assertThat(rec.recruiterEmail()).isEqualTo("alex@stripe.com");
        assertThat(rec.notes()).isEqualTo("Application confirmed for Senior Backend Engineer at Stripe.");
        assertThat(rec.confidenceScore()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("Should extract rejection status and clean notes correctly")
    void shouldExtractRejectionStatus() {
        String json = """
                {
                  "isJobApplication": true,
                  "companyName": "MUFG Pension & Market Services",
                  "roleTitle": "JAVA Developer",
                  "status": "REJECTED",
                  "location": null,
                  "jobUrl": "https://mufg.com/careers/456",
                  "recruiterName": null,
                  "recruiterEmail": null,
                  "notes": "Application update regarding JAVA Developer role at MUFG. • Job URL: https://mufg.com/careers/456",
                  "confidenceScore": 0.92
                }
                """;
        mockChatResponse(json);

        Optional<JobApplicationExtractionRecord> result = extractor.extract(
                "Job Application Update - JAVA Developer",
                "hcmn.fa.sender@workflow.email.oraclecloud.com",
                "We regret to advise that your job application has been unsuccessful."
        );

        assertThat(result).isPresent();
        JobApplicationExtractionRecord rec = result.get();
        assertThat(rec.companyName()).isEqualTo("MUFG Pension & Market Services");
        assertThat(rec.roleTitle()).isEqualTo("JAVA Developer");
        assertThat(rec.status()).isEqualTo("REJECTED");
        assertThat(rec.notes()).doesNotContain("• Job URL: https://mufg.com/careers/456");
        assertThat(rec.notes()).contains("Application update regarding JAVA Developer role at MUFG.");
    }

    @Test
    @DisplayName("Should return empty when AI determines email is not a job application")
    void shouldReturnEmptyWhenAiDeterminesNotJobApplication() {
        String json = """
                {
                  "isJobApplication": false,
                  "companyName": null,
                  "roleTitle": null,
                  "status": "APPLIED",
                  "location": null,
                  "jobUrl": null,
                  "recruiterName": null,
                  "recruiterEmail": null,
                  "notes": null,
                  "confidenceScore": 0.0
                }
                """;
        mockChatResponse(json);

        Optional<JobApplicationExtractionRecord> result = extractor.extract(
                "General update",
                "info@company.com",
                "Just checking in with some company news."
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when AI confidence score is below threshold")
    void shouldReturnEmptyWhenConfidenceIsBelowThreshold() {
        String json = """
                {
                  "isJobApplication": true,
                  "companyName": "Unknown",
                  "roleTitle": "Developer",
                  "status": "APPLIED",
                  "location": null,
                  "jobUrl": null,
                  "recruiterName": null,
                  "recruiterEmail": null,
                  "notes": "Uncertain email content",
                  "confidenceScore": 0.40
                }
                """;
        mockChatResponse(json);

        Optional<JobApplicationExtractionRecord> result = extractor.extract(
                "Inquiry response",
                "hello@unknown.com",
                "Maybe related to a role"
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should fast reject aggregated job digest without calling AI")
    void shouldFastRejectAggregatedJobDigestWithoutCallingAi() {
        Optional<JobApplicationExtractionRecord> result = extractor.extract(
                "You applied for 7 jobs on 05 Oct",
                "Naukri <info@naukri.com>",
                "Here is the summary of 7 jobs you applied for on 05 Oct."
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(chatClient);
    }

    @Test
    @DisplayName("Should fast reject negative keywords without calling AI")
    void shouldFastRejectNegativeKeywordsWithoutCallingAi() {
        Optional<JobApplicationExtractionRecord> result = extractor.extract(
                "Interview Cake Weekly Problem #630: Inflight Entertainment",
                "Jacob <yourfriends@interviewcake.com>",
                "Here is this week's coding problem."
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(chatClient);
    }

    @Test
    @DisplayName("Should fast reject newsletter sender without calling AI")
    void shouldFastRejectNewsletterSenderWithoutCallingAi() {
        Optional<JobApplicationExtractionRecord> result = extractor.extract(
                "Top job picks for you",
                "jobalerts-noreply@linkedin.com",
                "Check out these jobs."
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(chatClient);
    }

    @Test
    @DisplayName("Should throw AiExtractionException when AI call throws an exception")
    void shouldThrowAiExtractionExceptionWhenAiCallFails() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenThrow(new RuntimeException("Connection timed out"));

        assertThatThrownBy(() -> extractor.extract(
                "Application Received",
                "careers@meta.com",
                "We received your application."
        ))
                .isInstanceOf(AiExtractionException.class)
                .hasMessageContaining("AI model execution failed");
    }

    @Test
    @DisplayName("Should throw AiExtractionException when AI response is not valid JSON")
    void shouldThrowAiExtractionExceptionWhenAiReturnsInvalidJson() {
        mockChatResponse("Sorry, I cannot process this request.");

        assertThatThrownBy(() -> extractor.extract(
                "Application Received",
                "careers@meta.com",
                "We received your application."
        ))
                .isInstanceOf(AiExtractionException.class)
                .hasMessageContaining("Failed to parse AI JSON response");
    }

    @Test
    @DisplayName("Should return empty when ChatClient is null")
    void shouldReturnEmptyWhenChatClientIsNull() {
        JobApplicationExtractor nullExtractor = new JobApplicationExtractor(null);

        Optional<JobApplicationExtractionRecord> result = nullExtractor.extract(
                "Application Received",
                "careers@meta.com",
                "We received your application."
        );

        assertThat(result).isEmpty();
    }
}
