import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatAnchor } from "@angular/material/button";
import { RouterLink } from "@angular/router";
import { MatIcon } from "@angular/material/icon";
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApplicationService } from '../applications/services/application.service';
import { ApplicationResponse } from '../../shared/models/application.model';
import { TERMINAL_STATUSES } from '../../shared/constants/status-transitions';
import { StatusBadgeComponent } from "../../shared/components/status-badge/status-badge.component";
import { DatePipe } from '@angular/common';

interface StatCardConfig {
  label: string;
  value: () => number;
  modifier?: string;
}

@Component({
  selector: 'app-dashboard',
  imports: [
    MatAnchor,
    RouterLink,
    MatIcon,
    MatProgressBarModule,
    StatusBadgeComponent,
    DatePipe
],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private appService = inject(ApplicationService);

  loading = signal(true);
  applications = signal<ApplicationResponse[]>([]);

  total = computed(() => this.applications().length);
  active = computed(() => this.applications().filter(a => !TERMINAL_STATUSES.includes(a.status)).length);
  offers = computed(() => this.applications().filter(a => a.status === 'OFFER').length);
  accepted = computed(() => this.applications().filter(a => a.status === 'ACCEPTED').length);

  statCards: StatCardConfig[] =[
    { label: 'Total', value: () => this.total() },
    { label: 'Active', value: () => this.active() },
    { label: 'Offers', value: () => this.offers(), modifier: 'offer' },
    { label: 'Accepted', value: () => this.accepted(), modifier: 'accepted' }
  ];

  recent = computed(() => 
    [...this.applications()]
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
      .slice(0, 5)
  );

  ngOnInit(): void {
    this.appService.getApplications({ size: 200 }).subscribe({
      next: page => {
        this.applications.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)  
    });
  }
}
