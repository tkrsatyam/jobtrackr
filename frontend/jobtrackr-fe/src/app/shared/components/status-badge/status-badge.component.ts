import { Component, computed, input } from '@angular/core';
import { ApplicationStatus } from '../../models/application.model';
import { STATUS_LABELS } from '../../constants/enum-labels';

@Component({
  selector: 'app-status-badge',
  imports: [],
  templateUrl: './status-badge.component.html',
  styleUrl: './status-badge.component.scss',
})
export class StatusBadgeComponent {
  status = input.required<ApplicationStatus>();
  
  displayLabel = computed(() => STATUS_LABELS[this.status()]);
}
