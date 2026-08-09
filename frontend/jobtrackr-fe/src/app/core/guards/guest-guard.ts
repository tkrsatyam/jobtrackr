import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenStorageService } from '../services/token-storage.service';

/**
 * Inverse of authGuard. Keeps already-authenticated users off guest-only
 * routes (landing page, login, register) by redirecting them straight to
 * /dashboard before the route component is instantiated.
 */
export const guestGuard: CanActivateFn = () => {
  const tokenStorage = inject(TokenStorageService);
  const router = inject(Router);

  if (tokenStorage.isLoggedIn()) return router.createUrlTree(['/dashboard']);
  return true;
};
