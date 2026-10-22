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
        assertThat(record.confidenceScore()).isGreaterThanOrEqualTo(0.60);
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
    @DisplayName("Should extract Citi Workday application with correct company and role")
    void testExtractCitiWorkdayEmail() {
        String subject = "A Task Awaits You: Complete Questionnaire - Review Candidate for Job Application: Satyam Kumar \u200e- 25886810 Core Java Developer (C112055873)";
        String sender = "Citi Human Resources <citi@myworkday.com>";
        String body = "Review Candidate for Job Application: Satyam Kumar. Please complete your questionnaire.";

        Optional<JobApplicationExtractionRecord> result = extractor.extract(subject, sender, body);

        assertThat(result).isPresent();
        JobApplicationExtractionRecord record = result.get();
        assertThat(record.companyName()).isEqualTo("Citi");
        assertThat(record.roleTitle()).isEqualTo("Core Java Developer");
        assertThat(record.confidenceScore()).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    @DisplayName("Should extract LinkedIn Easy Apply confirmation with company from subject")
    void testExtractLinkedInApplication() {
        String subject = "Satyam, your application was sent to Integrated Consultancy Services";
        String sender = "LinkedIn <jobs-noreply@linkedin.com>";
        String body = "Your application for the position has been sent to Integrated Consultancy Services.";

        Optional<JobApplicationExtractionRecord> result = extractor.extract(subject, sender, body);

        assertThat(result).isPresent();
        JobApplicationExtractionRecord record = result.get();
        assertThat(record.companyName()).isEqualTo("Integrated Consultancy Services");
        assertThat(record.confidenceScore()).isGreaterThanOrEqualTo(0.60);
    }

    @Test
    @DisplayName("Should return empty for non-job related emails and promotions")
    void testIgnoreNonJobEmails() {
        // Amazon package
        assertThat(extractor.extract(
                "Your Amazon.com order has shipped",
                "auto-confirm@amazon.com",
                "Your package is on the way."
        )).isEmpty();

        // Bank pre-approved loan
        assertThat(extractor.extract(
                "xx2068: Tap to See Your Pre-Approved Offer",
                "HDFC Bank <information@mailers.hdfcbank.bank.in>",
                "Special pre-approved loan offer for you."
        )).isEmpty();

        // News article about business offers
        assertThat(extractor.extract(
                "Tata Motors sweetens its offer for Iveco",
                "Mint <info@connect.livemint.com>",
                "Business news digest today."
        )).isEmpty();

        // Phone advertisement
        assertThat(extractor.extract(
                "Your iPhone offer is calling!",
                "IDFC FIRST Bank <alwaysyoufirst@emailers.idfcfirst.bank.in>",
                "Get cashback on latest iPhone."
        )).isEmpty();

        // LeetCode contest
        assertThat(extractor.extract(
                "Satyam Kumar, Join LeetCode Contest and get ready for interview prep!",
                "LeetCode <no-reply@leetcode.com>",
                "Weekly contest starts on Sunday."
        )).isEmpty();

        // Interview Cake weekly problem
        assertThat(extractor.extract(
                "Interview Cake Weekly Problem #630: Inflight Entertainment",
                "Jacob from Interview Cake <yourfriends@interviewcake.com>",
                "Here is this week's coding problem."
        )).isEmpty();

        // University MBA application deadline
        assertThat(extractor.extract(
                "PGP TBM application deadline extended to 28th September, 12:00 PM",
                "Masters' Union <admissions@mastersunion.org>",
                "Submit your PGP TBM admission application today."
        )).isEmpty();
    }
}
