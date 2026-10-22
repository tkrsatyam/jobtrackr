import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { EmailIngestionService } from './email-ingestion.service';
import { PendingReviewService } from './pending-review.service';
import { IngestionSyncResponse } from '../../../shared/models/application.model';

describe('EmailIngestionService', () => {
  let service: EmailIngestionService;
  let httpClientMock: { post: ReturnType<typeof vi.fn> };
  let pendingReviewServiceMock: { refreshCount: ReturnType<typeof vi.fn> };
  let snackBarMock: { open: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    httpClientMock = {
      post: vi.fn()
    };
    pendingReviewServiceMock = {
      refreshCount: vi.fn().mockReturnValue(of({ count: 2 }))
    };
    snackBarMock = {
      open: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        EmailIngestionService,
        { provide: HttpClient, useValue: httpClientMock },
        { provide: PendingReviewService, useValue: pendingReviewServiceMock },
        { provide: MatSnackBar, useValue: snackBarMock }
      ]
    });
    service = TestBed.inject(EmailIngestionService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should trigger sync and refresh review count when applications discovered', () => {
    const response: IngestionSyncResponse = {
      status: 'SUCCESS',
      discoveredCount: 3,
      message: 'Discovered 3 applications'
    };
    httpClientMock.post.mockReturnValue(of(response));

    service.syncEmails().subscribe();

    expect(service.syncing()).toBe(false);
    expect(httpClientMock.post).toHaveBeenCalled();
    expect(pendingReviewServiceMock.refreshCount).toHaveBeenCalled();
    expect(snackBarMock.open).toHaveBeenCalledWith('Found 3 new applications in your inbox.', 'OK', { duration: 5000 });
  });

  it('should show info message when no new emails are discovered', () => {
    const response: IngestionSyncResponse = {
      status: 'SUCCESS',
      discoveredCount: 0,
      message: 'No new emails'
    };
    httpClientMock.post.mockReturnValue(of(response));

    service.syncEmails().subscribe();

    expect(snackBarMock.open).toHaveBeenCalledWith('No new job application emails discovered.', 'OK', { duration: 3500 });
  });

  it('should handle error gracefully and reset syncing signal', () => {
    httpClientMock.post.mockReturnValue(throwError(() => new Error('Sync error')));

    service.syncEmails().subscribe({
      error: () => {}
    });

    expect(service.syncing()).toBe(false);
    expect(snackBarMock.open).toHaveBeenCalled();
  });
});
