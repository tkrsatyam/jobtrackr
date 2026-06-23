import { Component, inject, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../features/auth/services/auth.service';
import { MatDialog } from '@angular/material/dialog';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { ThemeService } from '../../core/services/theme.service';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'app-topbar',
  imports: [
    RouterLink,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatTooltipModule
  ],
  templateUrl: './topbar.component.html',
  styleUrl: './topbar.component.scss',
})
export class TopbarComponent {
  private authService = inject(AuthService);
  private dialog = inject(MatDialog);
  readonly themeService = inject(ThemeService);

  menuToggle = output<void>();

  currentUser = this.authService.currentUser;

  logout(): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Logout',
        message: 'Are you sure you want to log out?',
        confirmLabel: 'Logout'
      }
    });
    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.authService.logout();
    });
  }
}
