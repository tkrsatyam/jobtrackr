import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { PriorityBadgeComponent } from '../../../shared/components/priority-badge/priority-badge.component';
import { TagChipComponent } from '../../../shared/components/tag-chip/tag-chip.component';
import { ApplicationService } from '../services/application.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApplicationResponse, ApplicationStatus } from '../../../shared/models/application.model';
import { ACTIVE_STATUSES, STATUS_LABELS } from '../../../shared/constants/enum-labels';
import { getAllowedTransitions, TERMINAL_STATUSES } from '../../../shared/constants/status-transitions';
import { MatDialog } from '@angular/material/dialog';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { ApplicationCardComponent } from "../components/application-card/application-card.component";

@Component({
  selector: 'app-application-board',
  imports: [
    RouterLink,
    DragDropModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    ApplicationCardComponent
],
  templateUrl: './application-board.component.html',
  styleUrl: './application-board.component.scss',
})
export class ApplicationBoardComponent implements OnInit {
  private appService = inject(ApplicationService);
  private snackBar = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  loading = signal(true);
  columns = signal<Map<ApplicationStatus, ApplicationResponse[]>>(new Map());

  boardStatuses = ACTIVE_STATUSES;
  terminalStatuses = TERMINAL_STATUSES;
  statusLabels = STATUS_LABELS;
  columnIds = [...ACTIVE_STATUSES, ...TERMINAL_STATUSES].map(status => `col-${status}`);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.appService.getApplications({ size: 200 }).subscribe({
      next: page => {
        const map = new Map<ApplicationStatus, ApplicationResponse[]>();
        ACTIVE_STATUSES.forEach(status => map.set(status, []));
        page.content.forEach(app => {
          if (map.has(app.status)) {
            map.get(app.status)!.push(app);
          }
        });
        this.columns.set(map);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  getColumnCards(status: ApplicationStatus): ApplicationResponse[] {
    return this.columns().get(status) ?? [];
  }

  getEmptyCards(): ApplicationResponse[] {    // for terminal statuses
    return [];
  }

  onDrop(event: CdkDragDrop<ApplicationResponse[]>, targetStatus: ApplicationStatus): void {
    if (event.previousContainer === event.container) return;

    const app: ApplicationResponse = event.item.data;
    const allowed = getAllowedTransitions(app.status);

    if (!allowed.includes(targetStatus)) {
      this.snackBar.open(
        `Cannot move from ${STATUS_LABELS[app.status]} to ${STATUS_LABELS[targetStatus]}`,
        'OK',
        { duration: 3000 }
      );
      return;
    }

    const newColumns = new Map(this.columns());
    const sourceList = [...(newColumns.get(app.status) ?? [])];
    const targetList = [...(newColumns.get(targetStatus) ?? [])];
    const idx = sourceList.findIndex(a => a.applicationId === app.applicationId);

    if (idx !== -1) sourceList.splice(idx, 1);
    const updatedApp = { ...app, status: targetStatus };
    targetList.splice(event.currentIndex, 0, updatedApp);

    newColumns.set(app.status, sourceList);
    newColumns.set(targetStatus, targetList);
    this.columns.set(newColumns);

    this.appService.changeStatus(app.applicationId, { status: targetStatus }).subscribe({
      next: a => {
        if (TERMINAL_STATUSES.includes(targetStatus)) {
          this.snackBar.open(`${a.companyName} application status updated to ${STATUS_LABELS[a.status]}`, 'OK', { duration: 3000 });
        }
        this.load();
      },
      error: () => {
        this.load();
        this.snackBar.open('Status update failed', 'OK', { duration: 3000 });
      }
    });
  }

  onArchive(app: ApplicationResponse): void {
    this.appService.toggleArchive(app.applicationId).subscribe(() => {
      this.snackBar.open('Application archived', 'OK', { duration: 3000 });
      this.load();
    });
  }

  onDelete(app: ApplicationResponse): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Delete Application',
        message: `Delete ${app.companyName} — ${app.role}? This cannot be undone.`,
        confirmLabel: 'Delete',
        destructive: true
      }
    });
    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.appService.delete(app.applicationId).subscribe(() => {
        this.snackBar.open('Application deleted', 'OK', { duration: 3000 });
        this.load();
      });
    });
  }
}
