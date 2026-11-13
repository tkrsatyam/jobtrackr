package com.jobtrackr.email_ingestion_service.dto;

import com.jobtrackr.email_ingestion_service.entity.EmailProcessingStatus;

public record StatusCountDTO(EmailProcessingStatus status, long count) {
}
