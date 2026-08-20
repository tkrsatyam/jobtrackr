import { Component, input, output, signal } from '@angular/core';
import { ApplicationStatus } from '../../../../shared/models/application.model';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { ACTIVE_STATUSES, STATUS_LABELS } from '../../../../shared/constants/enum-labels';

export type BulkAction = { type: 'delete' } | { type: 'archive' } | { type: 'unarchive' } | { type: 'status'; status: ApplicationStatus } | { type: 'tag' };

@Component({
  selector: 'app-bulk-action-toolbar',
  imports: [
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule
  ],
  templateUrl: './bulk-action-toolbar.component.html',
  styleUrl: './bulk-action-toolbar.component.scss',
})
export class BulkActionToolbarComponent {
  selectedCount = input.required<number>();
  allArchived = input.required<boolean>();
  action = output<BulkAction>();

  showStatusPicker = signal(false);
  selectedStatus = signal<ApplicationStatus | ''>('');

  activeStatuses = ACTIVE_STATUSES;
  statusLabels = STATUS_LABELS;

  onArchive(): void {
    this.action.emit({ type: 'archive' });
  }

  onUnarchive(): void {
    this.action.emit({ type: 'unarchive' });
  }

  onDelete(): void {
    this.action.emit({ type:'delete' });
  }

  onAddTag(): void {
    this.action.emit({ type: 'tag' });
  }

  toggleStatusPicker(): void {
    this.showStatusPicker.update(v => !v);
    this.selectedStatus.set('');
  }

  applyStatusChange(): void {
    const status = this.selectedStatus();
    if (!status) return;
    this.action.emit({ type: 'status', status });
    this.showStatusPicker.set(false);
    this.selectedStatus.set('');
  }
}
