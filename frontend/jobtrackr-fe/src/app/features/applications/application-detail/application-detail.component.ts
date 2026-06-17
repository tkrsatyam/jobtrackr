import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { SalaryFormatPipe } from '../../../shared/pipes/salary-format/salary-format-pipe';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { PriorityBadgeComponent } from '../../../shared/components/priority-badge/priority-badge.component';
import { TagChipComponent } from '../../../shared/components/tag-chip/tag-chip.component';
import { StatusTimelineComponent } from '../../../shared/components/status-timeline/status-timeline.component';
import { StatusChangePanelComponent } from '../components/status-change-panel/status-change-panel.component';
import { ApplicationService } from '../services/application.service';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApplicationResponse, ApplicationStatus } from '../../../shared/models/application.model';
import { SOURCE_LABELS, WORK_MODE_LABELS } from '../../../shared/constants/enum-labels';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { MatTooltip } from "@angular/material/tooltip";
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-application-detail',
  imports: [
    RouterLink,
    DatePipe,
    SalaryFormatPipe,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
    MatProgressBarModule,
    StatusBadgeComponent,
    PriorityBadgeComponent,
    TagChipComponent,
    StatusTimelineComponent,
    StatusChangePanelComponent,
    MatTooltip,
    FormsModule
],
  templateUrl: './application-detail.component.html',
  styleUrl: './application-detail.component.scss',
})
export class ApplicationDetailComponent implements OnInit {
  private appService = inject(ApplicationService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);

  application = signal<ApplicationResponse | null>(null);
  loading = signal(true);
  showTagInput = signal(false);
  newTagValue = signal('');

  workModeLabels = WORK_MODE_LABELS;
  sourceLabels = SOURCE_LABELS;
  
  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.appService.getById(id).subscribe({
      next: app => {
        this.application.set(app);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  onStatusChange(event: { status: ApplicationStatus; note: string }): void {
    const app = this.application();
    if (!app) return;
    this.appService.changeStatus(app.applicationId, event).subscribe({
      next: updated => {
        this.application.set(updated);
        this.snackBar.open('Status updated', 'OK', { duration: 3000 });
      }
    });
  }

  onTagRemoved(tag: string): void {
    const app = this.application();
    if (!app) return;
    this.appService.removeTag(app.applicationId, tag).subscribe(
      updated => this.application.set(updated)
    );
  }

  onTagAdded(): void {
    const tag = this.newTagValue().trim().toLowerCase();
    const app = this.application();
    if (!tag || !app) return;
    if (app.tags.includes(tag)) {
      this.newTagValue.set('');
      return;
    }
    this.appService.addTag(app.applicationId, tag).subscribe(updated => {
      this.application.set(updated);
      this.newTagValue.set('');
      this.showTagInput.set(false);
    });
  }

  onTagInputKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter') this.onTagAdded();
    if (event.key === 'Escape') {
      this.showTagInput.set(false);
      this.newTagValue.set('');
    }
  }

  toggleArchive(): void {
    const app = this.application();
    if (!app) return;
    this.appService.toggleArchive(app.applicationId).subscribe(
      updated => this.application.set(updated)
    );
  }

  delete(): void {
    const app = this.application();
    if (!app) return;
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Delete Application',
        message: 'This will permanently remove the application. This cannot be undone.',
        confirmLabel: 'Delete',
        destructive: true
      }
    });
    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.appService.delete(app.applicationId).subscribe(() => {
        this.snackBar.open('Application deleted', 'OK', { duration: 3000 });
        this.router.navigate(['/applications']);
      });
    });
  }
}
