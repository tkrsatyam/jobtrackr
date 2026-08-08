import { Component, inject, OnInit } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { MatIconModule } from "@angular/material/icon";

interface FeatureHighlight {
  icon: string;
  title: string;
  description: string;
}

@Component({
  selector: 'app-landing',
  imports: [MatIconModule],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.scss',
})
export class LandingComponent implements OnInit {
  private title = inject(Title);
  private meta = inject(Meta);

  readonly features: FeatureHighlight[] = [
    {
      icon: 'work_outline',
      title: 'Track applications',
      description: 'Keep every application, company, and role in one organized place instead of scattered spreadsheets.',
    },
    {
      icon: 'view_kanban',
      title: 'Kanban board',
      description: 'Drag applications through your pipeline — from saved to offer — and see your progress at a glance.',
    },
    {
      icon: 'history',
      title: 'Status history',
      description: 'Every status change is tracked automatically, so you always know when and why something moved.',
    },
    {
      icon: 'folder_open',
      title: 'Documents & reminders',
      description: 'Attach resumes and cover letters, and get reminded before interviews and follow-ups slip by.',
    },
  ];

  ngOnInit(): void {
    this.title.setTitle('JobTrackr — Organize your job search');
    this.meta.updateTag({
      name: 'description',
      content: 'JobTrackr helps you track job applications, visualize your pipeline on a kanban board, and never miss a follow-up.',
    });
  }
}
