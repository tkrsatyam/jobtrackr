import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { STATUS_LABELS } from '../../shared/constants/enum-labels';

export function humanizeStatusMessage(message: string): string {
  if (!message) return message;
  let formatted = message;
  for (const [code, label] of Object.entries(STATUS_LABELS)) {
    formatted = formatted.replace(new RegExp(`\\b${code}\\b`, 'g'), label);
  }
  return formatted;
}

export const errorInterceptor: HttpInterceptorFn = (req, next) => {

  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) return throwError(() => error);

      let message = 'Something went wrong. Please try again.';
      if (error.error?.detail) message = error.error.detail;
      else if (error.error?.error) message = error.error.error;
      else if (error.error?.message) message = error.error.message;
      else if (error.status === 403) message = 'You do not have permission to do that.';
      else if (error.status === 404) message = 'Resource not found.';
      else if (error.status === 0) message = 'Cannot reach server. Check your connection.';

      snackBar.open(humanizeStatusMessage(message), 'Dismiss', { duration: 4000 });
      return throwError(() => error);
    })
  );
};
