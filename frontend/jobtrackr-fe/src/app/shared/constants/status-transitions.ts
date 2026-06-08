import { ApplicationStatus } from "../models/application.model";

export const STATUS_TRANSITIONS: Record<ApplicationStatus, ApplicationStatus[]> = {
    SAVED:           ['APPLIED', 'WITHDRAWN'],
    APPLIED:         ['PHONE_SCREEN', 'INTERVIEW', 'REJECTED', 'GHOSTED', 'WITHDRAWN'],
    PHONE_SCREEN:    ['INTERVIEW', 'TECHNICAL_ROUND', 'REJECTED', 'GHOSTED', 'WITHDRAWN'],
    INTERVIEW:       ['TECHNICAL_ROUND', 'HR_ROUND', 'OFFER', 'REJECTED', 'GHOSTED', 'WITHDRAWN'],
    TECHNICAL_ROUND: ['HR_ROUND', 'OFFER', 'REJECTED', 'GHOSTED', 'WITHDRAWN'],
    HR_ROUND:        ['OFFER', 'REJECTED', 'GHOSTED', 'WITHDRAWN'],
    OFFER:           ['ACCEPTED', 'REJECTED', 'WITHDRAWN'],
    ACCEPTED:        [],
    REJECTED:        [],
    GHOSTED:         [],
    WITHDRAWN:       []
}

export const TERMINAL_STATUSES: ApplicationStatus[] = ['ACCEPTED', 'REJECTED', 'GHOSTED', 'WITHDRAWN'];

export function isTerminal(status: ApplicationStatus): boolean {
    return TERMINAL_STATUSES.includes(status);
}

export function getAllowedTransitions(status: ApplicationStatus): ApplicationStatus[] {
    return STATUS_TRANSITIONS[status] ?? [];
}