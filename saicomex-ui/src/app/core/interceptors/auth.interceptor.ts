import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, tap, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

/** Attaches the bearer token to every API call. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).getToken();
  const authed = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(authed);
};

/**
 * Turns API failures into something the operator can act on.
 *
 * The API sets `X-Auth-Status` to distinguish an expired session from a
 * genuine permission failure. Without that distinction both arrive as a 401
 * or 403 and the operator is told "you don't have permission" when the real
 * answer is "sign in again" — the single most confusing thing an internal
 * system can say.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const toast = inject(ToastService);

  return next(req).pipe(
    tap(event => {
      const status = (event as { headers?: { get(name: string): string | null } }).headers?.get?.('X-Auth-Status');
      if (status === 'token-expired' || status === 'token-invalid') auth.sessionExpired();
    }),
    catchError((error: HttpErrorResponse) => {
      const authStatus = error.headers?.get?.('X-Auth-Status');

      if (error.status === 401 || authStatus === 'token-expired' || authStatus === 'token-invalid') {
        // Suppress the toast on the login screen itself; the form shows it.
        if (!req.url.includes('/auth/login')) {
          auth.sessionExpired();
          toast.error('Your session has ended. Please sign in again.');
        }
        return throwError(() => error);
      }

      if (error.status === 0) {
        toast.error('Cannot reach the server. Check your connection and try again.');
        return throwError(() => error);
      }

      // The API always sends a `message` an operator can read; fall back only
      // when something upstream (nginx, a proxy) produced the response.
      const message = error.error?.message
        ?? (error.status === 403 ? 'You do not have permission to do that.' : 'Something went wrong.');

      if (!req.url.includes('/auth/login')) toast.error(message);
      return throwError(() => error);
    }),
  );
};
