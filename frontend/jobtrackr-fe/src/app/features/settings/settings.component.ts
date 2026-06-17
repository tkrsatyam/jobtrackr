import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../auth/services/auth.service';
import { TokenStorageService } from '../../core/services/token-storage.service';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { MatIconModule } from '@angular/material/icon';

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
  private router = inject(Router);

  currentUser = this.authService.currentUser;
  isLocalUser = computed(() => this.currentUser()?.provider === 'LOCAL');

  showProfileForm = signal(false);
  showPasswordForm = signal(false);

  profileSaving = signal(false);
  passwordSaving = signal(false);

  profileForm = this.fb.group({
    fullName: ['', [Validators.required, Validators.minLength(2)]],
    avatarUrl: ['']
  });

  passwordForm = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]]
  });

  ngOnInit(): void {
    if (!this.currentUser()) {
      this.authService.getProfile().subscribe();
    }
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
    this.authService.changePassword(raw.currentPassword, raw.newPassword).subscribe({
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
