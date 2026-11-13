import { Component, inject, OnInit } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { RouterLink } from '@angular/router';
import { InboxSyncService } from './services/inbox-sync.service';
import { PendingReviewService } from '../applications/services/pending-review.service';
import { ReviewModalComponent } from '../applications/components/review-modal/review-modal.component';
import { ProcessedEmailItem } from '../../shared/models/inbox-sync.model';

@Component({
  selector: 'app-inbox-sync',
  imports: [
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatChipsModule,
    MatProgressBarModule,
    MatTooltipModule,
    RouterLink
  ],
  templateUrl: './inbox-sync.component.html',
  styleUrl: './inbox-sync.component.scss'
})
export class InboxSyncComponent implements OnInit {
  readonly syncService = inject(InboxSyncService);
  readonly reviewService = inject(PendingReviewService);
  private dialog = inject(MatDialog);

  readonly status = this.syncService.status;
  readonly history = this.syncService.history;
  readonly loading = this.syncService.loading;
  readonly syncing = this.syncService.syncing;
  readonly totalElements = this.syncService.totalElements;
  readonly totalPages = this.syncService.totalPages;
  readonly currentPage = this.syncService.currentPage;
  readonly selectedFilter = this.syncService.selectedFilter;
  readonly hasPendingReviews = this.reviewService.hasPending;
  readonly pendingCount = this.reviewService.count;

  readonly filters = [
    { label: 'All', value: 'ALL' },
    { label: 'Applications Detected', value: 'PROCESSED' },
    { label: 'Skipped & Filtered', value: 'SKIPPED' },
    { label: 'User Dismissed', value: 'REJECTED' }
  ];

  ngOnInit(): void {
    this.syncService.loadStatus().subscribe({ error: () => {} });
    this.syncService.loadHistory().subscribe({ error: () => {} });
    this.reviewService.refreshCount().subscribe({ error: () => {} });
  }

  onFilterChange(filterValue: string): void {
    this.syncService.selectedFilter.set(filterValue);
    this.syncService.currentPage.set(0);
    this.syncService.loadHistory(filterValue, 0).subscribe({ error: () => {} });
  }

  triggerSync(): void {
    this.syncService.syncEmails().subscribe({ error: () => {} });
  }

  reIngest(item: ProcessedEmailItem): void {
    this.syncService.reIngestEmail(item.messageId).subscribe({ error: () => {} });
  }

  openReviewModal(): void {
    const dialogRef = this.dialog.open(ReviewModalComponent, {
      width: '640px',
      maxWidth: '95vw',
      disableClose: true
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result?.reviewed) {
        this.syncService.loadStatus().subscribe({ error: () => {} });
        this.syncService.loadHistory().subscribe({ error: () => {} });
        this.reviewService.refreshCount().subscribe({ error: () => {} });
      }
    });
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages()) {
      this.syncService.currentPage.set(page);
      this.syncService.loadHistory(this.selectedFilter(), page).subscribe({ error: () => {} });
    }
  }

  getConfidenceClass(score: number | null): string {
    if (score === null || score === undefined) return 'conf-none';
    if (score >= 0.85) return 'conf-high';
    if (score >= 0.60) return 'conf-med';
    return 'conf-low';
  }
}
