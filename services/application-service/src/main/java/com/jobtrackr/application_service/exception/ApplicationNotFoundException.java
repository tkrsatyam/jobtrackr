package com.jobtrackr.application_service.exception;

import java.util.UUID;

public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException(String message) {
        super(message);
    }

    public ApplicationNotFoundException(UUID applicationId) {
        super("Application not found with id: " + applicationId);
    }
}