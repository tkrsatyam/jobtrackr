package com.jobtrackr.application_service.service;

import com.jobtrackr.application_service.entity.enums.ApplicationStatus;
import com.jobtrackr.application_service.exception.InvalidStatusTransitionException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class StatusTransitionValidator {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(ApplicationStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(ApplicationStatus.SAVED,
                EnumSet.of(ApplicationStatus.APPLIED, ApplicationStatus.WITHDRAWN));

        ALLOWED_TRANSITIONS.put(ApplicationStatus.APPLIED,
                EnumSet.of(ApplicationStatus.PHONE_SCREEN, ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED,
                        ApplicationStatus.GHOSTED, ApplicationStatus.WITHDRAWN));

        ALLOWED_TRANSITIONS.put(ApplicationStatus.PHONE_SCREEN,
                EnumSet.of(ApplicationStatus.INTERVIEW, ApplicationStatus.TECHNICAL_ROUND, ApplicationStatus.REJECTED,
                        ApplicationStatus.GHOSTED, ApplicationStatus.WITHDRAWN));

        ALLOWED_TRANSITIONS.put(ApplicationStatus.INTERVIEW,
                EnumSet.of(ApplicationStatus.TECHNICAL_ROUND, ApplicationStatus.HR_ROUND, ApplicationStatus.OFFER,
                        ApplicationStatus.REJECTED, ApplicationStatus.GHOSTED, ApplicationStatus.WITHDRAWN));

        ALLOWED_TRANSITIONS.put(ApplicationStatus.TECHNICAL_ROUND,
                EnumSet.of(ApplicationStatus.HR_ROUND, ApplicationStatus.OFFER, ApplicationStatus.REJECTED,
                        ApplicationStatus.GHOSTED, ApplicationStatus.WITHDRAWN));

        ALLOWED_TRANSITIONS.put(ApplicationStatus.HR_ROUND,
                EnumSet.of(ApplicationStatus.OFFER, ApplicationStatus.REJECTED, ApplicationStatus.GHOSTED,
                        ApplicationStatus.WITHDRAWN));

        ALLOWED_TRANSITIONS.put(ApplicationStatus.OFFER,
                EnumSet.of(ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN));

        ALLOWED_TRANSITIONS.put(ApplicationStatus.ACCEPTED, EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED_TRANSITIONS.put(ApplicationStatus.REJECTED, EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED_TRANSITIONS.put(ApplicationStatus.GHOSTED, EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED_TRANSITIONS.put(ApplicationStatus.WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class));
    }

    public void validate(ApplicationStatus current, ApplicationStatus next) {
        Set<ApplicationStatus> allowed = ALLOWED_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(next)) {
            throw new InvalidStatusTransitionException(current, next);
        }
    }

    public Set<ApplicationStatus> getAllowedTransitions(ApplicationStatus current) {
        return ALLOWED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(ApplicationStatus.class));
    }
}