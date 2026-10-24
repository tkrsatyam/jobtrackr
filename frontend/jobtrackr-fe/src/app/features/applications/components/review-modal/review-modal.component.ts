import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { PendingReviewService } from '../../services/pending-review.service';
import { ApplicationResponse, ApplicationSource, ApplicationStatus, ConfirmReviewRequest } from '../../../../shared/models/application.model';
import { ALL_STATUSES, ALL_SOURCES, SOURCE_LABELS, STATUS_LABELS } from '../../../../shared/constants/enum-labels';
import { DatePipe } from '@angular/common';

export interface ReviewModalData {
  applications?: ApplicationResponse[];
}

@Component({
  selector: 'app-review-modal',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatChipsModule,
    MatProgressBarModule,
    DatePipe
  ],
  templateUrl: './review-modal.component.html',
  styleUrl: './review-modal.component.scss'
})
export class ReviewModalComponent implements OnInit {
  private fb = inject(FormBuilder);
  readonly dialogRef = inject(MatDialogRef<ReviewModalComponent>);
  readonly data = inject<ReviewModalData>(MAT_DIALOG_DATA, { optional: true });
  readonly reviewService = inject(PendingReviewService);

  readonly allStatuses = ALL_STATUSES;
  readonly statusLabels = STATUS_LABELS;
  readonly allSources = ALL_SOURCES;
  readonly sourceLabels = SOURCE_LABELS;

  readonly items = signal<ApplicationResponse[]>([]);
  readonly currentIndex = signal<number>(0);
  readonly isSubmitting = signal<boolean>(false);
  readonly showRawSnippet = signal<boolean>(false);

  readonly currentItem = computed(() => {
    const list = this.items();
    const idx = this.currentIndex();
    return list.length > idx ? list[idx] : null;
  });

  readonly totalCount = computed(() => this.items().length);

  readonly confidencePercent = computed(() => {
    const item = this.currentItem();
    if (!item || item.confidenceScore === undefined || item.confidenceScore === null) return null;
    return Math.round(item.confidenceScore * 100);
  });

  readonly confidenceClass = computed(() => {
    const score = this.currentItem()?.confidenceScore ?? 0;
    if (score >= 0.8) return 'confidence-high';
    if (score >= 0.5) return 'confidence-medium';
    return 'confidence-low';
  });

  form = this.fb.nonNullable.group({
    companyName: ['', [Validators.required]],
    role: ['', [Validators.required]],
    jobUrl: [''],
    location: [''],
    status: ['APPLIED' as ApplicationStatus, [Validators.required]],
    source: ['EMAIL' as ApplicationSource, [Validators.required]],
    salaryMin: [null as number | null],
    salaryMax: [null as number | null],
    currency: ['INR'],
    notes: ['']
  });

  ngOnInit(): void {
    if (this.data?.applications && this.data.applications.length > 0) {
      this.items.set(this.data.applications);
      this.populateForm();
    } else {
      this.isSubmitting.set(true);
      this.reviewService.loadPendingList().subscribe({
        next: list => {
          this.items.set(list);
          this.isSubmitting.set(false);
          this.populateForm();
        },
        error: () => {
          this.isSubmitting.set(false);
        }
      });
    }
  }

  toggleSnippet(): void {
    this.showRawSnippet.update(v => !v);
  }

  private populateForm(): void {
    const item = this.currentItem();
    if (!item) return;

    this.form.patchValue({
      companyName: item.companyName || '',
      role: item.role || '',
      jobUrl: item.jobUrl || '',
      location: item.location || '',
      status: item.status || 'APPLIED',
      source: item.source || 'EMAIL',
      salaryMin: item.salaryMin ?? null,
      salaryMax: item.salaryMax ?? null,
      currency: item.currency || 'INR',
      notes: item.notes || ''
    });
  }

  confirmCurrent(): void {
    const item = this.currentItem();
    if (!item || this.form.invalid || this.isSubmitting()) return;

    this.isSubmitting.set(true);
    const val = this.form.getRawValue();
    const req: ConfirmReviewRequest = {
      companyName: val.companyName,
      role: val.role,
      jobUrl: val.jobUrl || undefined,
      location: val.location || undefined,
      status: val.status,
      source: val.source,
      salaryMin: val.salaryMin ?? undefined,
      salaryMax: val.salaryMax ?? undefined,
      currency: val.currency || undefined,
      notes: val.notes || undefined
    };

    this.reviewService.confirm(item.applicationId, req).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.advanceOrClose();
      },
      error: () => {
        this.isSubmitting.set(false);
      }
    });
  }

  rejectCurrent(): void {
    const item = this.currentItem();
    if (!item || this.isSubmitting()) return;

    this.isSubmitting.set(true);
    this.reviewService.reject(item.applicationId).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.advanceOrClose();
      },
      error: () => {
        this.isSubmitting.set(false);
      }
    });
  }

  private advanceOrClose(): void {
    const remaining = this.items().filter((_, i) => i !== this.currentIndex());
    this.items.set(remaining);

    if (remaining.length === 0) {
      this.dialogRef.close({ reviewed: true });
    } else {
      if (this.currentIndex() >= remaining.length) {
        this.currentIndex.set(0);
      }
      this.populateForm();
    }
  }

  close(): void {
    this.dialogRef.close({ reviewed: false });
  }
}
