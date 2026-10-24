package com.jobtrackr.email_ingestion_service.exception;

public class AiExtractionException extends RuntimeException {

    public AiExtractionException(String message) {
        super(message);
    }

    public AiExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
