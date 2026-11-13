import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { InboxSyncComponent } from './inbox-sync.component';
import { InboxSyncService } from './services/inbox-sync.service';
import { PendingReviewService } from '../applications/services/pending-review.service';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { provideRouter } from '@angular/router';
import { IngestionStatus, IngestionHistoryPage, ProcessedEmailItem } from '../../shared/models/inbox-sync.model';

describe('InboxSyncComponent', () => {
  let component: InboxSyncComponent;
  let fixture: ComponentFixture<InboxSyncComponent>;
  let mockSyncService: any;
  let mockReviewService: any;
  let mockDialog: any;

  const mockStatus: IngestionStatus = {
    connected: true,
    connectedEmail: 'khushi@example.com',
    lastPolledAt: '2026-10-26T18:00:00Z',
    totalScanned: 25,
    totalProcessed: 13,
    totalSkipped: 12,
    totalRejected: 0,
    totalFailed: 0
  };

  const mockHistoryPage: IngestionHistoryPage = {
    content: [
      {
        id: '1',
        messageId: 'msg-1',
        subject: 'Application Received',
        sender: 'jobs@synopsys.com',
        receivedAt: '2026-10-26T12:00:00Z',
        status: 'PROCESSED',
        confidenceScore: 0.98,
        rejectionReason: null,
        processedAt: '2026-10-26T18:00:00Z'
      }
    ],
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
    last: true
  };

  beforeEach(async () => {
    mockSyncService = {
      status: signal(mockStatus),
      history: signal(mockHistoryPage.content),
      loading: signal(false),
      syncing: signal(false),
      totalElements: signal(1),
      totalPages: signal(1),
      currentPage: signal(0),
      selectedFilter: signal<string | null>(null),
      loadStatus: vi.fn().mockReturnValue(of(mockStatus)),
      loadHistory: vi.fn().mockReturnValue(of(mockHistoryPage)),
      syncEmails: vi.fn().mockReturnValue(of({ status: 'SUCCESS', discoveredCount: 1, message: 'Done' })),
      reIngestEmail: vi.fn().mockReturnValue(of(mockHistoryPage.content[0]))
    };

    mockReviewService = {
      hasPending: signal(false),
      count: signal(0),
      refreshCount: vi.fn().mockReturnValue(of({ count: 0 }))
    };

    mockDialog = {
      open: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [InboxSyncComponent],
      providers: [
        provideRouter([]),
        { provide: InboxSyncService, useValue: mockSyncService },
        { provide: PendingReviewService, useValue: mockReviewService },
        { provide: MatDialog, useValue: mockDialog }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(InboxSyncComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load initial status and history', () => {
    expect(component).toBeTruthy();
    expect(mockSyncService.loadStatus).toHaveBeenCalled();
    expect(mockSyncService.loadHistory).toHaveBeenCalled();
  });

  it('should filter history on filter change', () => {
    component.onFilterChange('SKIPPED');
    expect(mockSyncService.selectedFilter()).toBe('SKIPPED');
    expect(mockSyncService.loadHistory).toHaveBeenCalledWith('SKIPPED', 0);
  });

  it('should compute correct confidence classes', () => {
    expect(component.getConfidenceClass(0.95)).toBe('conf-high');
    expect(component.getConfidenceClass(0.70)).toBe('conf-med');
    expect(component.getConfidenceClass(0.40)).toBe('conf-low');
    expect(component.getConfidenceClass(null)).toBe('conf-none');
  });

  it('should call reIngestEmail when reIngest is clicked', () => {
    const item: ProcessedEmailItem = mockHistoryPage.content[0];
    component.reIngest(item);
    expect(mockSyncService.reIngestEmail).toHaveBeenCalledWith('msg-1');
  });
});
