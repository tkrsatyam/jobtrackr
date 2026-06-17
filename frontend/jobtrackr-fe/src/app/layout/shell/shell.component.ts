import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { TopbarComponent } from '../topbar/topbar.component';
import { AuthService } from '../../features/auth/services/auth.service';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    SidebarComponent,
    TopbarComponent
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent implements OnInit {
  private authService = inject(AuthService);

  sidebarOpen = signal(true);

  ngOnInit(): void {
    if (!this.authService.currentUser()) {
      this.authService.getProfile().subscribe();
    }
  }

  toggleSidebar(): void {
    this.sidebarOpen.update(v => !v);
  }
}
