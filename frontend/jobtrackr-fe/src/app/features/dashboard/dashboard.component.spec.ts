import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardComponent } from './dashboard.component';
import { ApplicationService } from '../applications/services/application.service';
import { PendingReviewService } from '../applications/services/pending-review.service';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { signal } from '@angular/core';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;

  beforeEach(async () => {
    const mockAppService = {
      getApplications: vi.fn().mockReturnValue(of({ content: [], totalElements: 0 }))
    };

    const countSignal = signal(0);
    const hasPendingSignal = signal(false);
    const mockReviewService = {
      count: countSignal,
      hasPending: hasPendingSignal,
      refreshCount: vi.fn().mockReturnValue(of({ count: 0 }))
    };

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        { provide: ApplicationService, useValue: mockAppService },
        { provide: PendingReviewService, useValue: mockReviewService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load applications', () => {
    expect(component).toBeTruthy();
    expect(component.total()).toBe(0);
    expect(component.loading()).toBe(false);
  });
});
