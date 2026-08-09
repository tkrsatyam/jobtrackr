import { Component, inject, OnInit } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

interface FeatureCard {
  icon: string;
  title: string;
  description: string;
}

interface HowItWorksStep {
  icon: string;
  title: string;
  description: string;
}

interface Stat {
  value: string;
  label: string;
  source: string;
}

interface PipelineStage {
  label: string;
  colorVar: string;
  darkText: boolean;
}

@Component({
  selector: 'app-landing',
  imports: [RouterLink, MatButtonModule, MatIconModule],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.scss',
})
export class LandingComponent implements OnInit {
  private titleService = inject(Title);
  private meta = inject(Meta);

  readonly stats: Stat[] = [
    {
      value: '180+',
      label: 'applications per hire on average',
      source: 'CareerPlug, 2024',
    },
    {
      value: '5 months',
      label: 'average length of a job search',
      source: 'U.S. Bureau of Labor Statistics',
    },
    {
      value: '6 apps',
      label: 'sent per offer received, on average',
      source: 'U.S. Bureau of Labor Statistics',
    },
  ];

  readonly steps: HowItWorksStep[] = [
    {
      icon: 'add_circle_outline',
      title: 'Add an application',
      description:
        'Log any job in seconds — company, role, priority, source, and a link to the posting before it disappears.',
    },
    {
      icon: 'swap_horiz',
      title: 'Move it through your pipeline',
      description:
        'Drag cards on the kanban board or update statuses in the list view as you progress through each stage.',
    },
    {
      icon: 'notifications_active',
      title: 'Never miss a follow-up',
      description:
        'Set reminders for interviews and check-ins. Attach your tailored resume and cover letter right to the application.',
    },
  ];

  readonly pipeline: PipelineStage[] = [
    { label: 'Saved',      colorVar: '--color-saved',           darkText: true  },
    { label: 'Applied',    colorVar: '--color-applied',         darkText: false },
    { label: 'Phone',      colorVar: '--color-phone-screen',    darkText: false },
    { label: 'Interview',  colorVar: '--color-interview',       darkText: false },
    { label: 'Technical',  colorVar: '--color-technical-round', darkText: false },
    { label: 'HR Round',   colorVar: '--color-hr-round',        darkText: true  },
    { label: 'Offer',      colorVar: '--color-offer',           darkText: false },
    { label: 'Accepted',   colorVar: '--color-accepted',        darkText: false },
  ];

  readonly features: FeatureCard[] = [
    {
      icon: 'work_outline',
      title: 'Track applications',
      description:
        'Keep every application, company, and role in one organized place instead of scattered spreadsheets.',
    },
    {
      icon: 'view_kanban',
      title: 'Kanban board',
      description:
        'Drag applications through your pipeline — from saved to offer — and see your progress at a glance.',
    },
    {
      icon: 'history',
      title: 'Status history',
      description:
        'Every status change is logged automatically, so you always know when and why something moved.',
    },
    {
      icon: 'folder_open',
      title: 'Documents & reminders',
      description:
        'Attach resumes and cover letters, and get reminded before interviews and follow-ups slip by.',
    },
  ];

  ngOnInit(): void {
    this.titleService.setTitle('JobTrackr — Organize your job search');
    this.meta.updateTag({
      name: 'description',
      content:
        'JobTrackr helps you track job applications, visualize your pipeline on a kanban board, and never miss a follow-up.',
    });
  }
}
