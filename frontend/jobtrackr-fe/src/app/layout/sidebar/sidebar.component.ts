import { Component, inject } from '@angular/core';
import { MatRippleModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { SidebarService } from '../../core/services/sidebar.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  exact: boolean;
}

@Component({
  selector: 'app-sidebar',
  imports: [
    RouterLink,
    RouterLinkActive,
    MatIconModule,
    MatRippleModule,
    MatTooltipModule
  ],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent {
  readonly sidebarService = inject(SidebarService);

  navItems: NavItem[] = [
    { label: 'Dashboard', icon: 'dashboard', route:'/dashboard', exact: true },
    { label: 'Applications', icon: 'work_outline', route: '/applications', exact: true },
    { label: 'Board', icon: 'view_kanban', route: '/applications/board', exact: true },
    { label: 'Settings', icon: 'settings', route: '/settings', exact: true }
  ];

}
