import { inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, finalize, tap } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  EmailProcessingStatus,
  IngestionHistoryPage,
  IngestionStatus,
  IngestionSyncResponse,
  ProcessedEmailItem
} from '../../../shared/models/inbox-sync.model';
import { PendingReviewService } from '../../applications/services/pending-review.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApplicationResponse } from '../../../shared/models/application.model';

@Injectable({
  providedIn: 'root'
})
export class InboxSyncService {
  private http = inject(HttpClient);
  private pendingReviewService = inject(PendingReviewService);
  private snackBar = inject(MatSnackBar);

  private baseUrl = `${environment.apiUrl}/api/ingestion`;
  private appBaseUrl = `${environment.apiUrl}/api/applications`;

  readonly status = signal<IngestionStatus | null>(null);
  readonly history = signal<ProcessedEmailItem[]>([]);
  readonly loading = signal<boolean>(false);
  readonly syncing = signal<boolean>(false);
  readonly totalElements = signal<number>(0);
  readonly totalPages = signal<number>(0);
  readonly currentPage = signal<number>(0);
  readonly pageSize = signal<number>(10);
  readonly selectedFilter = signal<string>('ALL');

  loadStatus(): Observable<IngestionStatus> {
    return this.http.get<IngestionStatus>(`${this.baseUrl}/status`).pipe(
      tap(res => this.status.set(res))
    );
  }

  loadHistory(status = this.selectedFilter(), page = this.currentPage(), size = this.pageSize()): Observable<IngestionHistoryPage> {
    this.loading.set(true);
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (status && status !== 'ALL') {
      params = params.set('status', status);
    }

    return this.http.get<IngestionHistoryPage>(`${this.baseUrl}/history`, { params }).pipe(
      tap({
        next: res => {
          this.history.set(res.content);
          this.totalElements.set(res.totalElements);
          this.totalPages.set(res.totalPages);
          this.currentPage.set(res.page);
        },
        error: () => {
          this.snackBar.open('Failed to load email ingestion history.', 'OK', { duration: 3500 });
        }
      }),
      finalize(() => this.loading.set(false))
    );
  }

  syncEmails(): Observable<IngestionSyncResponse> {
    this.syncing.set(true);
    return this.http.post<IngestionSyncResponse>(`${this.baseUrl}/sync`, {}).pipe(
      tap({
        next: res => {
          this.loadStatus().subscribe({ error: () => {} });
          this.loadHistory().subscribe({ error: () => {} });
          this.pendingReviewService.refreshCount().subscribe({ error: () => {} });

          if (res.discoveredCount > 0) {
            this.snackBar.open(
              `Found ${res.discoveredCount} application${res.discoveredCount > 1 ? 's' : ''} in your inbox.`,
              'OK',
              { duration: 5000 }
            );
          } else {
            this.snackBar.open('Inbox scan complete. No new application emails discovered.', 'OK', { duration: 3500 });
          }
        },
        error: () => {
          this.snackBar.open('Failed to sync emails. Please verify your Google account connection.', 'OK', {
            duration: 4000
          });
        }
      }),
      finalize(() => this.syncing.set(false))
    );
  }

  reIngestEmail(messageId: string): Observable<ProcessedEmailItem> {
    return this.http.post<ProcessedEmailItem>(`${this.baseUrl}/emails/${messageId}/re-ingest`, {}).pipe(
      tap({
        next: updated => {
          this.history.update(list => list.map(item => item.messageId === messageId ? updated : item));
          this.loadStatus().subscribe({ error: () => {} });
          this.pendingReviewService.refreshCount().subscribe({ error: () => {} });
          this.snackBar.open('Email re-processed successfully.', 'OK', { duration: 3500 });
        },
        error: () => {
          this.snackBar.open('Failed to re-process email.', 'OK', { duration: 3500 });
        }
      })
    );
  }

  restoreReview(applicationId: string): Observable<ApplicationResponse> {
    return this.http.post<ApplicationResponse>(`${this.appBaseUrl}/${applicationId}/restore-review`, {}).pipe(
      tap({
        next: () => {
          this.pendingReviewService.refreshCount().subscribe({ error: () => {} });
          this.loadStatus().subscribe({ error: () => {} });
          this.snackBar.open('Application restored to pending review.', 'OK', { duration: 3500 });
        },
        error: () => {
          this.snackBar.open('Failed to restore application.', 'OK', { duration: 3500 });
        }
      })
    );
  }
}
