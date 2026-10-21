import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-oauth2-callback',
  standalone: true,
  imports: [MatProgressSpinnerModule],
  templateUrl: './oauth2-callback.component.html',
  styleUrl: './oauth2-callback.component.scss'
})
export class Oauth2CallbackComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(AuthService);

  ngOnInit(): void {
    const accessToken = this.route.snapshot.queryParamMap.get('accessToken');
    const refreshToken = this.route.snapshot.queryParamMap.get('refreshToken');
    const error = this.route.snapshot.queryParamMap.get('error');

    if (accessToken && refreshToken) {
      this.authService.handleOAuthCallback(accessToken, refreshToken);
      this.authService.getProfile().subscribe({
        next: () => this.router.navigate(['/dashboard']),
        error: () => this.router.navigate(['/dashboard'])
      });
    } else {
      const errorMessage = error || 'Google authentication failed. Please try again.';
      this.router.navigate(['/login'], { queryParams: { error: errorMessage } });
    }
  }
}
