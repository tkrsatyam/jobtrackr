package com.jobtrackr.email_ingestion_service.dto;

public record JobApplicationExtractionRecord(
        boolean isJobApplication,
        String companyName,
        String roleTitle,
        String status,
        String location,
        String jobUrl,
        String recruiterName,
        String recruiterEmail,
        String notes,
        Double confidenceScore
) {}
