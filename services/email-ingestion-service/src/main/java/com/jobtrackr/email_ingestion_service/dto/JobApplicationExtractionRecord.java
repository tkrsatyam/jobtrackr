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
        Double confidenceScore,
        String rejectionReason
) {
    public JobApplicationExtractionRecord(
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
    ) {
        this(isJobApplication, companyName, roleTitle, status, location, jobUrl, recruiterName, recruiterEmail, notes, confidenceScore, null);
    }
}
