import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {

  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) return throwError(() => error);

      let message = 'Something went wrong. Please try again.';
      if (error.error?.error) message = error.error.error;
      else if (error.error?.message) message = error.error.message;
      else if (error.status === 403) message = 'You do not have permission to do that.';
      else if (error.status === 404) message = 'Resource not found.';
      else if (error.status === 0) message = 'Cannot reach server. Check your connection.';

      snackBar.open(message, 'Dismiss', { duration: 4000 });
      return throwError(() => error);
    })
  );
};