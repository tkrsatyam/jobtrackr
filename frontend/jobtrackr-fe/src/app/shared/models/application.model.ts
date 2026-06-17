export type ApplicationStatus = 
    | 'SAVED' | 'APPLIED' | 'PHONE_SCREEN' | 'INTERVIEW'
    | 'TECHNICAL_ROUND' | 'HR_ROUND' | 'OFFER'
    | 'ACCEPTED' | 'REJECTED' | 'GHOSTED' | 'WITHDRAWN';

export type PriorityLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'DREAM';
export type WorkMode = 'REMOTE' | 'HYBRID' | 'ON_SITE';
export type ApplicationSource = 
    | 'LINKEDIN' | 'NAUKRI' | 'INTERNSHALA' | 'COMPANY_WEBSITE'
    | 'REFERRAL' | 'ANGEL_LIST' | 'INSTAHYRE' | 'OTHER';

export interface StatusHistoryEntry {
    status: ApplicationStatus;
    note: string | null;
    changedAt: string;
}

export interface ApplicationResponse {
    applicationId: string;
    userId: string;
    companyName: string;
    role: string;
    jobUrl: string;
    status: ApplicationStatus;
    priority: PriorityLevel;
    workMode: WorkMode | null;
    location: string | null;
    salaryMin: number | null;
    salaryMax: number | null;
    currency: string;
    appliedDate: string | null;
    source: ApplicationSource;
    notes: string | null;
    isArchived: boolean;
    isDeleted: boolean;
    createdAt: string;
    updatedAt: string;
    tags: string[];
    statusHistory: StatusHistoryEntry[];
}

export interface Page<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}

export interface ApplicationFilter {
    status?: ApplicationStatus;
    priority?: PriorityLevel;
    workMode?: WorkMode | null;
    isArchived?: boolean;
    company?: string;
    role?: string;
    appliedAfter?: string;
    appliedBefore?: string;
    page?: number;
    size?: number;
    sortBy?: string;
    sortDir?: 'asc' | 'desc';
}

export interface CreateApplicationRequest {
    companyName: string;
    role: string;
    jobUrl?: string;
    status?: ApplicationStatus;
    priority?: PriorityLevel;
    workMode?: WorkMode;
    location?: string;
    salaryMin?: number;
    salaryMax?: number;
    currency?: string;
    appliedDate?: string;
    source?: ApplicationSource;
    notes?: string;
    tags?: string[];
}

export interface UpdateApplicationRequest {
    companyName?: string;
    role?: string;
    jobUrl?: string;
    priority?: PriorityLevel;
    workMode?: WorkMode;
    location?: string;
    salaryMin?: number;
    salaryMax?: number;
    currency?: string;
    appliedDate?: string;
    source?: ApplicationSource;
    notes?: string;
}

export interface ChangeStatusRequest {
    status: ApplicationStatus;
    note?: string;
}

export interface BulkIdsRequest {
    ids: string[];
}

export interface BulkStatusRequest {
    ids: string[];
    status: ApplicationStatus;
}