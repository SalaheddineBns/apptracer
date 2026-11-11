import { HttpContext } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, EMPTY, finalize, firstValueFrom, map, Observable, of, shareReplay, tap, throwError } from 'rxjs';
import { AuthenticationService, AuthTokensResponse, CurrentUserResponse, Role } from '../api/generated';
import { SKIP_ERROR_TOAST } from '../errors/error.interceptor';
import { roleSatisfies } from './roles';

// Keys also used by the Cypress command `cy.login`.
const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthenticationService);
  private readonly router = inject(Router);

  private readonly currentUser = signal<CurrentUserResponse | null>(null);
  private refresh$: Observable<string> | null = null;
  private ready = false;

  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  get accessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  get refreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  /** RG-03-10: true if the user's role satisfies the required role (hierarchy included). */
  hasRole(required: Role): boolean {
    const user = this.currentUser();
    return user !== null && roleSatisfies(user.role, required);
  }

  /** At start-up: reloads the user from the token in storage (never rejects). */
  init(): Promise<void> {
    const load$ = this.accessToken
      ? this.api.getCurrentUser().pipe(
          tap((user) => this.currentUser.set(user)),
          catchError(() => {
            this.clearSession();
            return EMPTY;
          }),
        )
      : EMPTY;
    return firstValueFrom(load$, { defaultValue: undefined }).then(() => {
      this.ready = true;
    });
  }

  login(email: string, password: string): Observable<CurrentUserResponse> {
    // Sign-in errors are shown by the screen (generic message RG-03-04), not in a toast.
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    return this.api.login({ email, password }, 'body', false, { context }).pipe(
      tap((tokens) => this.storeSession(tokens)),
      map((tokens) => tokens.user),
    );
  }

  /**
   * RG-03-15: changes the signed-in user's password (the old password is required). The current session
   * stays valid; only the other sessions are revoked by the back end.
   */
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.api.changeMyPassword({ currentPassword, newPassword }).pipe(map(() => undefined));
  }

  /**
   * RG-03-08: revokes the refresh token on the server, then clears local state and goes back to `/login` (CA-03-08).
   */
  logout(): Observable<void> {
    const refreshToken = this.refreshToken;
    const revoke$ = refreshToken
      ? this.api.logout({ refreshToken }, 'body', false, { context: new HttpContext().set(SKIP_ERROR_TOAST, true) })
      : of(null);
    return revoke$.pipe(
      catchError(() => of(null)),
      finalize(() => {
        this.clearSession();
        void this.router.navigate(['/login']);
      }),
      map(() => undefined),
    );
  }

  /**
   * RG-03-07: a single renewal at a time, shared by all pending requests
   * (the refresh token can only be used once). Emits the new access token.
   */
  refreshSession(): Observable<string> {
    if (!this.refresh$) {
      const refreshToken = this.refreshToken;
      if (!refreshToken) {
        return throwError(() => new Error('Aucun refresh token'));
      }
      this.refresh$ = this.api.refreshSession({ refreshToken }).pipe(
        tap((tokens) => this.storeSession(tokens)),
        map((tokens) => tokens.accessToken),
        finalize(() => (this.refresh$ = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.refresh$;
  }

  /** Unrecoverable session (renewal refused, CA-03-13): clears local state, then redirects to `/login`. */
  expireSession(): void {
    this.clearSession();
    if (this.ready) {
      const url = this.router.url;
      const returnUrl = url && !url.startsWith('/login') && url !== '/' ? url : undefined;
      void this.router.navigate(['/login'], { queryParams: returnUrl ? { returnUrl } : {} });
    }
  }

  private storeSession(tokens: AuthTokensResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
    this.currentUser.set(tokens.user);
  }

  private clearSession(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.currentUser.set(null);
  }
}
