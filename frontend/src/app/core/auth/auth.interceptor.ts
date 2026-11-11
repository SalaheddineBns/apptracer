import { HttpErrorResponse, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthStore } from './auth.store';

// RG-03-09: these calls carry no token and never trigger a renewal.
const PUBLIC_PATHS = ['/api/v1/auth/login', '/api/v1/auth/refresh', '/api/v1/auth/logout'];

function withToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

/**
 * Adds the access token; on 401, a single shared renewal (RG-03-07), then a replay (CA-03-06).
 * If the renewal fails: local sign-out and redirect to `/login` (CA-03-13).
 * To be declared AFTER `errorInterceptor` in `withInterceptors`.
 */
export const authInterceptor: HttpInterceptorFn = (req, next: HttpHandlerFn) => {
  if (PUBLIC_PATHS.some((path) => req.url.endsWith(path))) {
    return next(req);
  }
  const auth = inject(AuthStore);
  const sentToken = auth.accessToken;

  return next(withToken(req, sentToken)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      // A renewal already happened while this request was in flight: just replay.
      const current = auth.accessToken;
      const replay = (token: string) => next(withToken(req, token));
      if (current && current !== sentToken) {
        return replay(current);
      }
      return auth.refreshSession().pipe(
        catchError((refreshError: unknown) => {
          auth.expireSession();
          return throwError(() => refreshError);
        }),
        switchMap(replay),
      );
    }),
  );
};
