import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../auth/services/auth.service';
import { EmailIngestionService } from '../applications/services/email-ingestion.service';
import { TokenStorageService } from '../../core/services/token-storage.service';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { MatIconModule } from '@angular/material/icon';
import { passwordsMatchValidator } from '../../shared/validators/password-matches.validator';
import { CrossFieldErrorMatcher } from '../../shared/matchers/cross-field-error.matcher';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-settings',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCardModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatIconModule
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  private authService = inject(AuthService);
  private tokenStorage = inject(TokenStorageService);
  private fb = inject(NonNullableFormBuilder);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);
  readonly ingestionService = inject(EmailIngestionService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  currentUser = this.authService.currentUser;
  isLocalUser = computed(() => this.currentUser()?.provider === 'LOCAL');
  isGoogleConnected = computed(() => !!this.currentUser()?.googleConnected || this.currentUser()?.provider === 'GOOGLE');
  googleEmail = computed(() => this.currentUser()?.googleEmail ?? (this.currentUser()?.provider === 'GOOGLE' ? this.currentUser()?.email : null));

  showProfileForm = signal(false);
  showPasswordForm = signal(false);
  showCurrentPassword = signal(false);
  showNewPassword = signal(false);
  showConfirmNewPassword = signal(false);

  profileSaving = signal(false);
  passwordSaving = signal(false);
  connectingGoogle = signal(false);
  unlinkingGoogle = signal(false);

  passwordMismatchMatcher = new CrossFieldErrorMatcher('passwordMismatch');

  profileForm = this.fb.group({
    fullName: ['', [Validators.required, Validators.minLength(2)]],
    avatarUrl: ['']
  });

  passwordForm = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmNewPassword: ['', Validators.required]
  }, { validators: passwordsMatchValidator('newPassword', 'confirmNewPassword') });

  ngOnInit(): void {
    if (!this.currentUser()) {
      this.authService.getProfile().subscribe();
    }

    this.route.queryParams.subscribe(params => {
      if (params['accessToken'] && params['refreshToken']) {
        this.tokenStorage.saveTokens(params['accessToken'], params['refreshToken']);
      }

      if (params['linked'] === 'true') {
        this.snackBar.open('Google account connected successfully.', 'OK', { duration: 4000 });
        this.authService.getProfile().subscribe();
        this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
      } else if (params['error']) {
        const message = params['error'] === 'account_already_linked'
          ? 'This Google account is already linked to another JobTrackr user.'
          : 'Failed to connect Google account.';
        this.snackBar.open(message, 'OK', { duration: 5000 });
        this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
      }
    });
  }

  syncEmails(): void {
    this.ingestionService.syncEmails().subscribe();
  }

  connectGoogle(): void {
    this.connectingGoogle.set(true);
    this.authService.initLinkGoogle().subscribe({
      next: (res) => {
        const url = res.authUrl.startsWith('http')
          ? res.authUrl
          : `${environment.apiUrl}${res.authUrl}`;
        window.location.href = url;
      },
      error: () => {
        this.connectingGoogle.set(false);
        this.snackBar.open('Unable to start Google connection flow', 'OK', { duration: 3000 });
      }
    });
  }

  disconnectGoogle(): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Disconnect Google Account',
        message: 'This will revoke JobTrackr access to your Google mailbox. Automated email ingestion will be paused.',
        confirmLabel: 'Disconnect',
        destructive: true
      }
    });

    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.unlinkingGoogle.set(true);
      this.authService.unlinkGoogle().subscribe({
        next: () => {
          this.unlinkingGoogle.set(false);
          this.snackBar.open('Google account disconnected', 'OK', { duration: 3000 });
          this.authService.getProfile().subscribe();
        },
        error: () => {
          this.unlinkingGoogle.set(false);
          this.snackBar.open('Failed to disconnect Google account', 'OK', { duration: 3000 });
        }
      });
    });
  }

  openProfileForm(): void {
    const user = this.currentUser();
    if (user) {
      this.profileForm.patchValue({
        fullName: user.fullName,
        avatarUrl: user.avatarUrl ?? ''
      });
    }
    this.showProfileForm.set(true);
    this.showPasswordForm.set(false);
  }

  cancelProfileForm(): void {
    this.showProfileForm.set(false);
    this.profileForm.reset();
  }

  openPasswordForm(): void {
    this.showPasswordForm.set(true);
    this.showProfileForm.set(false);
  }

  cancelPasswordForm(): void {
    this.showPasswordForm.set(false);
    this.passwordForm.reset();
  }

  saveProfile(): void {
    if (this.profileForm.invalid) return;
    this.profileSaving.set(true);
    const raw = this.profileForm.getRawValue();
    this.authService.updateProfile({
      fullName: raw.fullName,
      avatarUrl: raw.avatarUrl || undefined
    }).subscribe({
      next: () => {
        this.profileSaving.set(false);
        this.showProfileForm.set(false);
        this.snackBar.open('Profile updated', 'OK', { duration: 3000 });
      },
      error: () => this.profileSaving.set(false)
    });
  }

  changePassword(): void {
    if (this.passwordForm.invalid) return;
    this.passwordSaving.set(true);
    const raw = this.passwordForm.getRawValue();
    this.authService.changePassword(raw.currentPassword, raw.newPassword, raw.confirmNewPassword).subscribe({
      next: () => {
        this.passwordSaving.set(false);
        this.showPasswordForm.set(false);
        this.passwordForm.reset();
        this.snackBar.open('Password changed', 'OK', { duration: 3000 });
      },
      error: () => this.passwordSaving.set(false)
    });
  }

  deleteAccount(): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Delete Account',
        message: 'This will permanently delete your account and all your application data. This cannot be undone.',
        confirmLabel: 'Delete Account',
        destructive: true
      }
    });
    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.authService.deleteAccount().subscribe(() => {
        this.tokenStorage.clearTokens();
        this.router.navigate(['/login']);
      });
    });
  }
}
