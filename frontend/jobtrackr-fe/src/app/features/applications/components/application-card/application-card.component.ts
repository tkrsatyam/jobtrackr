import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink } from '@angular/router';
import { PriorityBadgeComponent } from '../../../../shared/components/priority-badge/priority-badge.component';
import { TagChipComponent } from '../../../../shared/components/tag-chip/tag-chip.component';
import { ApplicationResponse } from '../../../../shared/models/application.model';

@Component({
  selector: 'app-application-card',
  imports: [
    RouterLink,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    PriorityBadgeComponent,
    TagChipComponent
  ],
  templateUrl: './application-card.component.html',
  styleUrl: './application-card.component.scss',
})
export class ApplicationCardComponent {
  app = input.required<ApplicationResponse>();

  statusChangeRequested = output<ApplicationResponse>();
  archiveRequested = output<ApplicationResponse>();
  deleteRequested = output<ApplicationResponse>();
}
