import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { ApplicationResponse } from '../../../../shared/models/application.model';

@Component({
  selector: 'app-proposal-banner',
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatMenuModule
  ],
  templateUrl: './proposal-banner.component.html',
  styleUrl: './proposal-banner.component.scss'
})
export class ProposalBannerComponent {
  readonly applications = input<ApplicationResponse[]>([]);

  readonly proposals = computed(() =>
    (this.applications() || []).filter(app => !!app.proposedStatus)
  );

  readonly hasProposals = computed(() => this.proposals().length > 0);
  readonly count = computed(() => this.proposals().length);
  readonly firstProposal = computed(() => this.proposals()[0]);
}
