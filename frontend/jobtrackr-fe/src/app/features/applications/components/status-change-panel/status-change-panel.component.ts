import { Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge.component';
import { ApplicationStatus } from '../../../../shared/models/application.model';
import { STATUS_LABELS } from '../../../../shared/constants/enum-labels';
import { getAllowedTransitions, isTerminal } from '../../../../shared/constants/status-transitions';
import { CdkTableModule } from "@angular/cdk/table";

@Component({
  selector: 'app-status-change-panel',
  imports: [
    FormsModule,
    MatButtonModule,
    MatInputModule,
    MatFormFieldModule,
    StatusBadgeComponent,
    CdkTableModule
],
  templateUrl: './status-change-panel.component.html',
  styleUrl: './status-change-panel.component.scss',
})
export class StatusChangePanelComponent {
  currentStatus = input.required<ApplicationStatus>();

  statusChange = output<{ status: ApplicationStatus; note: string }>();

  note = signal('');
  statusLabels = STATUS_LABELS;

  allowedTransitions(): ApplicationStatus[] {
    return getAllowedTransitions(this.currentStatus());
  }

  terminal(): boolean {
    return isTerminal(this.currentStatus());
  }

  changeStatus(status: ApplicationStatus): void {
    this.statusChange.emit({ status, note: this.note() });
    this.note.set('');
  }
}
