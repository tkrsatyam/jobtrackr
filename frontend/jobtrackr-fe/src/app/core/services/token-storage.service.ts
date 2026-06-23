import { computed, Injectable, signal } from '@angular/core';

const ACCESS_TOKEN_KEY = 'jobtrackr_access_token';
const REFRESH_TOKEN_KEY = 'jobtrackr_refresh_token';

@Injectable({
  providedIn: 'root',
})
export class TokenStorageService {
  private accessTokenSignal = signal<string | null> (
    localStorage.getItem(ACCESS_TOKEN_KEY)
  );

  readonly accessToken = this.accessTokenSignal.asReadonly();
  readonly isLoggedIn = computed(() => !!this.accessTokenSignal());

  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  saveTokens(accessToken: string, refreshToken: string) : void {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    this.accessTokenSignal.set(accessToken);
  }

  clearTokens(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.accessTokenSignal.set(null);
  }
}
