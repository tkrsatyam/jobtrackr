import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TokenStorageService } from '../../../core/services/token-storage.service';
import { environment } from '../../../../environments/environment';
import { AuthResponse, LoginRequest, RegisterRequest, UserProfile } from '../../../shared/models/auth.model';
import { Observable, tap } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private tokenStorage = inject(TokenStorageService);

  private baseUrl = `${environment.apiUrl}/api`;

  private currentUserSignal = signal<UserProfile | null>(null);
  readonly currentUser = this.currentUserSignal.asReadonly();

  register(req: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/auth/register`, req).pipe(
      tap(res => this.tokenStorage.saveTokens(res.accessToken, res.refreshToken))
    );
  }

  login(req: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/auth/login`, req).pipe(
      tap(res => this.tokenStorage.saveTokens(res.accessToken, res.refreshToken))
    );
  }

  logout(): void {
    const refreshToken = this.tokenStorage.getRefreshToken();
    if (refreshToken) {
      this.http.post(`${this.baseUrl}/auth/logout`, { refreshToken }).subscribe();
    }
    this.tokenStorage.clearTokens();
    this.currentUserSignal.set(null);
    this.router.navigate(['/login']);
  }

  getProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${this.baseUrl}/users/me`).pipe(
      tap(user => this.currentUserSignal.set(user))
    );
  }

  updateProfile(data: { fullName?: string; avatarUrl?: string }): Observable<UserProfile> {
    return this.http.put<UserProfile>(`${this.baseUrl}/users/me`, data).pipe(
      tap(user => this.currentUserSignal.set(user))
    );
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/users/me/password`, { currentPassword, newPassword });
  }

  deleteAccount(): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/users/me`);
  }
}
