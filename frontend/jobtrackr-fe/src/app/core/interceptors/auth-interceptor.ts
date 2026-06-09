import { HttpClient, HttpErrorResponse, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { BehaviorSubject, catchError, filter, switchMap, take, throwError } from 'rxjs';
import { TokenStorage } from '../services/token-storage';
import { Router } from '@angular/router';
import { AuthResponse } from '../../shared/models/auth.model';
import { environment } from '../../../environments/environment';

let isRefreshing = false;
const refreshToken$ = new BehaviorSubject<string | null>(null);

function addToken(req:HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

export const authInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {

  const tokenStorage = inject(TokenStorage);
  const router = inject(Router);
  const http = inject(HttpClient);

  const token = tokenStorage.getAccessToken();
  const authReq = token ? addToken(req, token) : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401) return throwError(() => error);

      if (isRefreshing) {
        return refreshToken$.pipe(
          filter( token => token !== null),
          take(1),
          switchMap(token => next(addToken(req, token)))
        );
      }
      
      isRefreshing = true;
      refreshToken$.next(null);

      const refreshToken = tokenStorage.getRefreshToken();
      if(!refreshToken) {
        tokenStorage.clearTokens();
        router.navigate(['/login']);
        return throwError(() => error);
      }

      return http.post<AuthResponse>(`${environment.apiUrl}/api/auth/refresh`, { refreshToken }).pipe(
        switchMap(res => {
          isRefreshing = false;
          tokenStorage.saveTokens(res.accessToken, res.refreshToken);
          refreshToken$.next(res.accessToken);
          return next(addToken(req, res.accessToken));
        }),
        catchError(err => {
          isRefreshing = false;
          tokenStorage.clearTokens();
          router.navigate(['/login']);
          return throwError(() => err);
        })
      );
    })
  );
};
