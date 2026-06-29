import { Component, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { TopbarComponent } from '../topbar/topbar.component';
import { AuthService } from '../../features/auth/services/auth.service';
import { SidebarService } from '../../core/services/sidebar.service';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    SidebarComponent,
    TopbarComponent,
    MatIconModule
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent implements OnInit {
  private authService = inject(AuthService);

  readonly sidebarService = inject(SidebarService);
  
  ngOnInit(): void {
    if (!this.authService.currentUser()) {
      this.authService.getProfile().subscribe();
    }
  }
}
