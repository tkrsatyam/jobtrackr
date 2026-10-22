import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ApplicationBoardComponent } from './application-board.component';
import { ApplicationService } from '../services/application.service';
import { PendingReviewService } from '../services/pending-review.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { signal } from '@angular/core';

describe('ApplicationBoardComponent', () => {
  let component: ApplicationBoardComponent;
  let fixture: ComponentFixture<ApplicationBoardComponent>;

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

    const mockSnackBar = {
      open: vi.fn()
    };

    const mockDialog = {
      open: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [ApplicationBoardComponent],
      providers: [
        provideRouter([]),
        { provide: ApplicationService, useValue: mockAppService },
        { provide: PendingReviewService, useValue: mockReviewService },
        { provide: MatSnackBar, useValue: mockSnackBar },
        { provide: MatDialog, useValue: mockDialog }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ApplicationBoardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and initialize columns', () => {
    expect(component).toBeTruthy();
    expect(component.boardStatuses.length).toBeGreaterThan(0);
    expect(component.loading()).toBe(false);
  });
});
