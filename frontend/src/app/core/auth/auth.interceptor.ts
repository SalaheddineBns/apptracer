import { HttpErrorResponse, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthStore } from './auth.store';

// RG-03-09 : ces appels ne portent pas de jeton et ne déclenchent jamais de renouvellement.
const PUBLIC_PATHS = ['/api/v1/auth/login', '/api/v1/auth/refresh', '/api/v1/auth/logout'];

function withToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

/**
 * Ajoute l'access token ; sur 401, un seul renouvellement partagé (RG-03-07) puis rejeu (CA-03-06).
 * Si le renouvellement échoue : déconnexion locale et redirection vers `/login` (CA-03-13).
 * À déclarer APRÈS `errorInterceptor` dans `withInterceptors`.
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
      // Un renouvellement a déjà eu lieu pendant que cette requête était en vol : simple rejeu.
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
