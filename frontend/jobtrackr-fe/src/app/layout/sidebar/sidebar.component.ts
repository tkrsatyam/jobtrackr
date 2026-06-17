import { Component } from '@angular/core';
import { MatRippleModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink, RouterLinkActive } from '@angular/router';

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
    MatRippleModule
  ],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent {
  navItems: NavItem[] = [
    { label: 'Dashboard', icon: 'dashboard', route:'/dashboard', exact: true },
    { label: 'Applications', icon: 'work_outline', route: '/applications', exact: true },
    { label: 'Board', icon: 'view_kanban', route: '/applications/board', exact: true },
    { label: 'Settings', icon: 'settings', route: '/settings', exact: true }
  ];
}
