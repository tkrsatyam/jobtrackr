import { ApplicationSource, ApplicationStatus, PriorityLevel, WorkMode } from "../models/application.model";

export const STATUS_LABELS: Record<ApplicationStatus, string> = {
    SAVED: 'Saved', APPLIED: 'Applied', PHONE_SCREEN: 'Phone Screen',
    INTERVIEW: 'Interview', TECHNICAL_ROUND: 'Technical Round', HR_ROUND: 'HR Round',
    OFFER: 'Offer', ACCEPTED: 'Accepted', REJECTED: 'Rejected',
    GHOSTED: 'Ghosted', WITHDRAWN: 'Withdrawn'
};

export const PRIORITY_LABELS: Record<PriorityLevel, string> = {
    LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', DREAM: 'Dream'
};

export const WORK_MODE_LABELS: Record<WorkMode, string> = {
    REMOTE: 'Remote', HYBRID: 'Hybrid', ON_SITE: 'On-Site'
};

export const SOURCE_LABELS: Record<ApplicationSource, string> = {
  LINKEDIN: 'LinkedIn', NAUKRI: 'Naukri', INTERNSHALA: 'Internshala',
  COMPANY_WEBSITE: 'Company Website', REFERRAL: 'Referral',
  ANGEL_LIST: 'AngelList', INSTAHYRE: 'Instahyre', OTHER: 'Other'
};

export const ALL_STATUSES: ApplicationStatus[] = [
  'SAVED','APPLIED','PHONE_SCREEN','INTERVIEW','TECHNICAL_ROUND','HR_ROUND',
  'OFFER','ACCEPTED','REJECTED','GHOSTED','WITHDRAWN'
];

export const ACTIVE_STATUSES: ApplicationStatus[] = [
  'SAVED','APPLIED','PHONE_SCREEN','INTERVIEW','TECHNICAL_ROUND','HR_ROUND','OFFER'
];

export const ALL_PRIORITIES: PriorityLevel[] = ['LOW','MEDIUM','HIGH','DREAM'];

export const ALL_WORK_MODES: WorkMode[] = ['REMOTE','HYBRID','ON_SITE'];

export const ALL_SOURCES: ApplicationSource[] = [
  'LINKEDIN','NAUKRI','INTERNSHALA','COMPANY_WEBSITE','REFERRAL','ANGEL_LIST','INSTAHYRE','OTHER'
];