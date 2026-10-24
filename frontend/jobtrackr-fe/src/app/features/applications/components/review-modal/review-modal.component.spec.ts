import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewModalComponent } from './review-modal.component';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { PendingReviewService } from '../../services/pending-review.service';
import { of } from 'rxjs';
import { ApplicationResponse } from '../../../../shared/models/application.model';

describe('ReviewModalComponent', () => {
  let component: ReviewModalComponent;
  let fixture: ComponentFixture<ReviewModalComponent>;
  let mockDialogRef: any;
  let mockReviewService: any;

  const mockApp: ApplicationResponse = {
    applicationId: 'app-1',
    userId: 'user-1',
    companyName: 'Stripe',
    role: 'Software Engineer',
    jobUrl: 'https://stripe.com/jobs/123',
    status: 'APPLIED',
    priority: 'HIGH',
    workMode: 'REMOTE',
    location: 'Remote',
    salaryMin: 120000,
    salaryMax: 160000,
    currency: 'USD',
    appliedDate: '2026-10-22',
    source: 'EMAIL',
    notes: 'Discovered from email confirmation',
    isArchived: false,
    isDeleted: false,
    createdAt: '2026-10-22T00:00:00Z',
    updatedAt: '2026-10-22T00:00:00Z',
    tags: [],
    statusHistory: [],
    aiDiscovered: true,
    requiresReview: true,
    confidenceScore: 0.92,
    rawEmailSnippet: 'Thank you for applying to Stripe'
  };

  beforeEach(async () => {
    mockDialogRef = {
      close: vi.fn()
    };

    mockReviewService = {
      loadPendingList: vi.fn().mockReturnValue(of([mockApp])),
      confirm: vi.fn().mockReturnValue(of({ ...mockApp, requiresReview: false })),
      reject: vi.fn().mockReturnValue(of(undefined))
    };

    await TestBed.configureTestingModule({
      imports: [ReviewModalComponent],
      providers: [
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { applications: [mockApp] } },
        { provide: PendingReviewService, useValue: mockReviewService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ReviewModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and populate form from initial data', () => {
    expect(component).toBeTruthy();
    expect(component.totalCount()).toBe(1);
    expect(component.currentItem()?.companyName).toBe('Stripe');
    expect(component.form.controls.companyName.value).toBe('Stripe');
    expect(component.form.controls.role.value).toBe('Software Engineer');
    expect(component.form.controls.source.value).toBe('EMAIL');
    expect(component.confidencePercent()).toBe(92);
    expect(component.confidenceClass()).toBe('confidence-high');
  });

  it('should toggle snippet display', () => {
    expect(component.showRawSnippet()).toBe(false);
    component.toggleSnippet();
    expect(component.showRawSnippet()).toBe(true);
    component.toggleSnippet();
    expect(component.showRawSnippet()).toBe(false);
  });

  it('should call confirm on service and close dialog when no items remain', () => {
    component.confirmCurrent();
    expect(mockReviewService.confirm).toHaveBeenCalledWith('app-1', expect.objectContaining({
      companyName: 'Stripe',
      role: 'Software Engineer',
      source: 'EMAIL'
    }));
    expect(mockDialogRef.close).toHaveBeenCalledWith({ reviewed: true });
  });

  it('should call reject on service and close dialog when no items remain', () => {
    component.rejectCurrent();
    expect(mockReviewService.reject).toHaveBeenCalledWith('app-1');
    expect(mockDialogRef.close).toHaveBeenCalledWith({ reviewed: true });
  });

  it('should close dialog when cancel button clicked', () => {
    component.close();
    expect(mockDialogRef.close).toHaveBeenCalledWith({ reviewed: false });
  });

  it('should include all statuses including terminal statuses in allStatuses', () => {
    expect(component.allStatuses).toContain('REJECTED');
    expect(component.allStatuses).toContain('ACCEPTED');
    expect(component.allStatuses).toContain('OFFER');
    expect(component.allStatuses).toContain('APPLIED');
  });

  it('should populate form with REJECTED status when item is rejected', () => {
    const rejectedApp: ApplicationResponse = { ...mockApp, status: 'REJECTED' };
    component.items.set([rejectedApp]);
    (component as any).populateForm();
    expect(component.form.controls.status.value).toBe('REJECTED');
  });
});
