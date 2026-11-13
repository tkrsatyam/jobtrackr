package com.jobtrackr.application_service.entity.enums;

import lombok.Getter;

@Getter
public enum ApplicationStatus {
    SAVED("Saved"),
    APPLIED("Applied"),
    PHONE_SCREEN("Phone Screen"),
    INTERVIEW("Interview"),
    TECHNICAL_ROUND("Technical Round"),
    HR_ROUND("HR Round"),
    OFFER("Offer"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    GHOSTED("Ghosted"),
    WITHDRAWN("Withdrawn");

    private final String displayName;

    ApplicationStatus(String displayName) {
        this.displayName = displayName;
    }
}
