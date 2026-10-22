import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, finalize, tap } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { IngestionSyncResponse } from '../../../shared/models/application.model';
import { PendingReviewService } from './pending-review.service';
import { MatSnackBar } from '@angular/material/snack-bar';

@Injectable({
  providedIn: 'root'
})
export class EmailIngestionService {
  private http = inject(HttpClient);
  private pendingReviewService = inject(PendingReviewService);
  private snackBar = inject(MatSnackBar);
  private baseUrl = `${environment.apiUrl}/api/ingestion`;

  readonly syncing = signal<boolean>(false);

  syncEmails(): Observable<IngestionSyncResponse> {
    this.syncing.set(true);
    return this.http.post<IngestionSyncResponse>(`${this.baseUrl}/sync`, {}).pipe(
      tap({
        next: res => {
          this.pendingReviewService.refreshCount().subscribe({
            error: () => {}
          });

          if (res.discoveredCount > 0) {
            this.snackBar.open(
              `Found ${res.discoveredCount} new application${res.discoveredCount > 1 ? 's' : ''} in your inbox.`,
              'OK',
              { duration: 5000 }
            );
          } else {
            this.snackBar.open('No new job application emails discovered.', 'OK', { duration: 3500 });
          }
        },
        error: () => {
          this.snackBar.open('Failed to sync emails. Please check your connection or Google link.', 'OK', {
            duration: 4000
          });
        }
      }),
      finalize(() => {
        this.syncing.set(false);
      })
    );
  }
}
