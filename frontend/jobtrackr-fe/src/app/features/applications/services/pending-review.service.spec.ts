import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { PendingReviewService } from './pending-review.service';
import { ApplicationService } from './application.service';
import { of } from 'rxjs';
import { ApplicationResponse, ConfirmReviewRequest } from '../../../shared/models/application.model';

describe('PendingReviewService', () => {
  let service: PendingReviewService;
  let appServiceSpy: any;

  const mockApp: ApplicationResponse = {
    applicationId: 'app-1',
    userId: 'user-1',
    companyName: 'Acme Corp',
    role: 'Frontend Engineer',
    jobUrl: 'https://example.com/job',
    status: 'APPLIED',
    priority: 'MEDIUM',
    workMode: 'REMOTE',
    location: 'Remote',
    salaryMin: 80000,
    salaryMax: 120000,
    currency: 'USD',
    appliedDate: '2026-10-22',
    source: 'EMAIL',
    notes: 'Parsed from confirmation email',
    isArchived: false,
    isDeleted: false,
    createdAt: '2026-10-22T00:00:00Z',
    updatedAt: '2026-10-22T00:00:00Z',
    tags: [],
    statusHistory: [],
    aiDiscovered: true,
    requiresReview: true,
    confidenceScore: 0.95,
    rawEmailSnippet: 'Thank you for applying to Acme Corp'
  };

  beforeEach(() => {
    appServiceSpy = {
      getPendingReviewCount: vi.fn().mockReturnValue(of({ count: 2 })),
      getPendingReview: vi.fn().mockReturnValue(of([mockApp])),
      confirmReview: vi.fn().mockReturnValue(of({ ...mockApp, requiresReview: false })),
      rejectReview: vi.fn().mockReturnValue(of(undefined))
    };

    TestBed.configureTestingModule({
      providers: [
        PendingReviewService,
        { provide: ApplicationService, useValue: appServiceSpy }
      ]
    });

    service = TestBed.inject(PendingReviewService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
    expect(service.count()).toBe(0);
    expect(service.hasPending()).toBe(false);
  });

  it('should refresh count signal', () => {
    service.refreshCount().subscribe(res => {
      expect(res.count).toBe(2);
      expect(service.count()).toBe(2);
      expect(service.hasPending()).toBe(true);
    });
  });

  it('should load pending list and update state', () => {
    service.loadPendingList().subscribe(list => {
      expect(list.length).toBe(1);
      expect(service.pendingList().length).toBe(1);
      expect(service.count()).toBe(1);
    });
  });

  it('should confirm application review and update state', () => {
    service.pendingList.set([mockApp]);
    service.count.set(1);

    let actionEmitted = false;
    service.reviewAction$.subscribe(() => {
      actionEmitted = true;
    });

    const req: ConfirmReviewRequest = { companyName: 'Acme Corp Updated' };
    service.confirm('app-1', req).subscribe(() => {
      expect(service.pendingList().length).toBe(0);
      expect(service.count()).toBe(0);
      expect(actionEmitted).toBe(true);
    });
  });

  it('should reject application review and update state', () => {
    service.pendingList.set([mockApp]);
    service.count.set(1);

    let actionEmitted = false;
    service.reviewAction$.subscribe(() => {
      actionEmitted = true;
    });

    service.reject('app-1').subscribe(() => {
      expect(service.pendingList().length).toBe(0);
      expect(service.count()).toBe(0);
      expect(actionEmitted).toBe(true);
    });
  });
});
