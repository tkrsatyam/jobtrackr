import { Component, computed, input } from '@angular/core';
import { PriorityLevel } from '../../models/application.model';
import { PRIORITY_LABELS } from '../../constants/enum-labels';

@Component({
  selector: 'app-priority-badge',
  imports: [],
  templateUrl: './priority-badge.component.html',
  styleUrl: './priority-badge.component.scss',
})
export class PriorityBadgeComponent {
  priority = input.required<PriorityLevel>();

  displayLabel = computed(() => PRIORITY_LABELS[this.priority()]);
}
