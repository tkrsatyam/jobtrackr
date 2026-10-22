import { Component, inject, OnInit, output } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { PendingReviewService } from '../../services/pending-review.service';
import { ReviewModalComponent } from '../review-modal/review-modal.component';
import { EmailIngestionService } from '../../services/email-ingestion.service';

@Component({
  selector: 'app-review-banner',
  imports: [
    MatButtonModule,
    MatIconModule
  ],
  templateUrl: './review-banner.component.html',
  styleUrl: './review-banner.component.scss'
})
export class ReviewBannerComponent implements OnInit {
  readonly reviewService = inject(PendingReviewService);
  readonly ingestionService = inject(EmailIngestionService);
  private dialog = inject(MatDialog);

  readonly count = this.reviewService.count;
  readonly hasPending = this.reviewService.hasPending;

  readonly reviewed = output<void>();

  ngOnInit(): void {
    this.reviewService.refreshCount().subscribe({
      error: () => {
        // Silently handle if user is unauthenticated or service unavailable
      }
    });
  }

  syncEmails(): void {
    this.ingestionService.syncEmails().subscribe();
  }

  openReviewModal(): void {
    const dialogRef = this.dialog.open(ReviewModalComponent, {
      width: '640px',
      maxWidth: '95vw',
      disableClose: true
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result?.reviewed) {
        this.reviewed.emit();
      }
    });
  }
}
