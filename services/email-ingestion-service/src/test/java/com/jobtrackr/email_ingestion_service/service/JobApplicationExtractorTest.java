package com.jobtrackr.email_ingestion_service.service;

import com.jobtrackr.email_ingestion_service.dto.JobApplicationExtractionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JobApplicationExtractorTest {

    private JobApplicationExtractor extractor;

    @BeforeEach
    void setUp() {
        // null ChatClient triggers heuristic fallback
        extractor = new JobApplicationExtractor(null);
    }

    @Test
    @DisplayName("Should extract application for role at company from subject")
    void testExtractSubjectWithRoleAndCompany() {
        String subject = "Application for Senior Backend Engineer at Stripe";
        String sender = "jobs@stripe.com";
        String body = "Thanks for applying to the Senior Backend Engineer position.";

        Optional<JobApplicationExtractionRecord> result = extractor.extract(subject, sender, body);

        assertThat(result).isPresent();
        JobApplicationExtractionRecord record = result.get();
        assertThat(record.isJobApplication()).isTrue();
        assertThat(record.companyName()).isEqualTo("Stripe");
        assertThat(record.roleTitle()).isEqualTo("Senior Backend Engineer");
        assertThat(record.status()).isEqualTo("APPLIED");
        assertThat(record.confidenceScore()).isGreaterThanOrEqualTo(0.5);
    }

    @Test
    @DisplayName("Should extract interview invitation status")
    void testExtractInterviewStatus() {
        String subject = "Interview Invitation: Acme Corp Software Engineer";
        String sender = "recruiting@acme.com";
        String body = "We would like to schedule an interview with you next week.";

        Optional<JobApplicationExtractionRecord> result = extractor.extract(subject, sender, body);

        assertThat(result).isPresent();
        JobApplicationExtractionRecord record = result.get();
        assertThat(record.status()).isEqualTo("INTERVIEW");
    }

    @Test
    @DisplayName("Should extract rejection status")
    void testExtractRejectionStatus() {
        String subject = "Update regarding your application with TechCorp";
        String sender = "careers@techcorp.com";
        String body = "Thank you for applying. Unfortunately, we will not be moving forward at this time.";

        Optional<JobApplicationExtractionRecord> result = extractor.extract(subject, sender, body);

        assertThat(result).isPresent();
        JobApplicationExtractionRecord record = result.get();
        assertThat(record.status()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("Should return empty for non-job related emails")
    void testIgnoreNonJobEmails() {
        String subject = "Your Amazon.com order has shipped";
        String sender = "auto-confirm@amazon.com";
        String body = "Your package containing books is on the way.";

        Optional<JobApplicationExtractionRecord> result = extractor.extract(subject, sender, body);

        assertThat(result).isEmpty();
    }
}
