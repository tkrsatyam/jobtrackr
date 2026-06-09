import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

const ACCESS_TOKEN_KEY = 'jobtrackr_access_token';
const REFRESH_TOKEN_KEY = 'jobtrackr_refresh_token';

@Injectable({
  providedIn: 'root',
})
export class TokenStorageService {
  
  private accessTokenSubject = new BehaviorSubject<string | null> (
    localStorage.getItem(ACCESS_TOKEN_KEY)
  );

  accessToken$ = this.accessTokenSubject.asObservable();

  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  saveTokens(accessToken: string, refreshToken: string) : void {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    this.accessTokenSubject.next(accessToken);
  }

  clearTokens(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.accessTokenSubject.next(null);
  }

  isLoggedIn(): boolean {
    return !!this.getAccessToken();
  }
}
