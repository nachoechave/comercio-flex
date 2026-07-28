import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

export const authErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        auth.markAnonymous();
      }
      return throwError(() => error);
    }),
  );
};
