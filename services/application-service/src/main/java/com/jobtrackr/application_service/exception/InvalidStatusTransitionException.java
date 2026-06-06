package com.jobtrackr.application_service.exception;

import com.jobtrackr.application_service.entity.enums.ApplicationStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(ApplicationStatus from, ApplicationStatus to) {
        super("Cannot transition from " + from + " to " + to);
    }
}