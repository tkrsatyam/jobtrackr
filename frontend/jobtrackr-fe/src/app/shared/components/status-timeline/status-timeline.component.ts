import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { StatusHistoryEntry } from '../../models/application.model';
import { STATUS_LABELS } from '../../constants/enum-labels';

@Component({
  selector: 'app-status-timeline',
  imports: [DatePipe],
  templateUrl: './status-timeline.component.html',
  styleUrl: './status-timeline.component.scss',
})
export class StatusTimelineComponent {
  entries = input.required<StatusHistoryEntry[]>();
  statusLabels = STATUS_LABELS;
}
