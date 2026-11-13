import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { InboxSyncService } from './inbox-sync.service';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { environment } from '../../../../environments/environment';
import { IngestionStatus, IngestionHistoryPage, ProcessedEmailItem } from '../../../shared/models/inbox-sync.model';

describe('InboxSyncService', () => {
  let service: InboxSyncService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MatSnackBarModule],
      providers: [
        InboxSyncService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(InboxSyncService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should load status and set signal', () => {
    const mockStatus: IngestionStatus = {
      connected: true,
      connectedEmail: 'test@gmail.com',
      lastPolledAt: '2026-10-26T18:00:00Z',
      totalScanned: 25,
      totalProcessed: 13,
      totalSkipped: 12,
      totalRejected: 0,
      totalFailed: 0
    };

    service.loadStatus().subscribe(status => {
      expect(status.connected).toBe(true);
      expect(status.connectedEmail).toBe('test@gmail.com');
      expect(service.status()).toEqual(mockStatus);
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/api/ingestion/status`);
    expect(req.request.method).toBe('GET');
    req.flush(mockStatus);
  });

  it('should load history and set signals', () => {
    const mockPage: IngestionHistoryPage = {
      content: [
        {
          id: '1',
          messageId: 'msg-1',
          subject: 'Interview at Google',
          sender: 'hr@google.com',
          receivedAt: '2026-10-26T10:00:00Z',
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

    service.loadHistory('PROCESSED', 0, 10).subscribe(res => {
      expect(res.content.length).toBe(1);
      expect(service.history().length).toBe(1);
      expect(service.totalElements()).toBe(1);
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/api/ingestion/history?page=0&size=10&status=PROCESSED`);
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should re-ingest email and update item in history', () => {
    const originalItem: ProcessedEmailItem = {
      id: '1',
      messageId: 'msg-1',
      subject: 'Interview',
      sender: 'hr@google.com',
      receivedAt: null,
      status: 'SKIPPED',
      confidenceScore: 0.4,
      rejectionReason: 'Low confidence',
      processedAt: '2026-10-26T18:00:00Z'
    };
    service.history.set([originalItem]);

    const updatedItem: ProcessedEmailItem = {
      ...originalItem,
      status: 'PROCESSED',
      confidenceScore: 0.95,
      rejectionReason: null
    };

    service.reIngestEmail('msg-1').subscribe(res => {
      expect(res.status).toBe('PROCESSED');
      expect(service.history()[0].status).toBe('PROCESSED');
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/api/ingestion/emails/msg-1/re-ingest`);
    expect(req.request.method).toBe('POST');
    req.flush(updatedItem);

    // loadStatus call
    const statusReq = httpTesting.expectOne(`${environment.apiUrl}/api/ingestion/status`);
    statusReq.flush({ connected: true, connectedEmail: 'test@gmail.com', lastPolledAt: null, totalScanned: 1, totalProcessed: 1, totalSkipped: 0, totalRejected: 0, totalFailed: 0 });

    // refreshCount call
    const countReq = httpTesting.expectOne(`${environment.apiUrl}/api/applications/pending-review/count`);
    countReq.flush({ count: 1 });
  });
});
