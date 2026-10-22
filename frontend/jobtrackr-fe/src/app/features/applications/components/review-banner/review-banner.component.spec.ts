import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewBannerComponent } from './review-banner.component';
import { PendingReviewService } from '../../services/pending-review.service';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { signal } from '@angular/core';

describe('ReviewBannerComponent', () => {
  let component: ReviewBannerComponent;
  let fixture: ComponentFixture<ReviewBannerComponent>;
  let mockReviewService: any;
  let mockDialog: any;

  beforeEach(async () => {
    const countSignal = signal(3);
    const hasPendingSignal = signal(true);

    mockReviewService = {
      count: countSignal,
      hasPending: hasPendingSignal,
      refreshCount: vi.fn().mockReturnValue(of({ count: 3 }))
    };

    mockDialog = {
      open: vi.fn().mockReturnValue({
        afterClosed: vi.fn().mockReturnValue(of({ reviewed: true }))
      })
    };

    await TestBed.configureTestingModule({
      imports: [ReviewBannerComponent],
      providers: [
        { provide: PendingReviewService, useValue: mockReviewService },
        { provide: MatDialog, useValue: mockDialog }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ReviewBannerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and display pending count', () => {
    expect(component).toBeTruthy();
    expect(component.count()).toBe(3);
    expect(component.hasPending()).toBe(true);

    const bannerDesc: HTMLElement = fixture.nativeElement.querySelector('.banner-desc');
    expect(bannerDesc.textContent).toContain('JobTrackr discovered 3 applications in your inbox');
  });

  it('should open review modal when button is clicked and emit reviewed', () => {
    let reviewedEmitted = false;
    component.reviewed.subscribe(() => {
      reviewedEmitted = true;
    });

    component.openReviewModal();

    expect(mockDialog.open).toHaveBeenCalled();
    expect(reviewedEmitted).toBe(true);
  });
});
