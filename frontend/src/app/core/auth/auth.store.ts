import { HttpContext } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, EMPTY, finalize, firstValueFrom, map, Observable, of, shareReplay, tap, throwError } from 'rxjs';
import { AuthenticationService, AuthTokensResponse, CurrentUserResponse, Role } from '../api/generated';
import { SKIP_ERROR_TOAST } from '../errors/error.interceptor';
import { roleSatisfies } from './roles';

// Clés utilisées aussi par la commande Cypress `cy.login`.
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

  /** RG-03-10 : vrai si le rôle de l'utilisateur satisfait le rôle requis (hiérarchie incluse). */
  hasRole(required: Role): boolean {
    const user = this.currentUser();
    return user !== null && roleSatisfies(user.role, required);
  }

  /** Au démarrage : recharge l'utilisateur à partir du jeton présent (ne rejette jamais). */
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
    return this.api.login({ email, password }).pipe(
      tap((tokens) => this.storeSession(tokens)),
      map((tokens) => tokens.user),
    );
  }

  /** RG-03-08 : révoque le refresh token côté serveur, puis purge locale et retour à `/login` (CA-03-08). */
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
   * RG-03-07 : un seul renouvellement à la fois, partagé par toutes les requêtes en attente
   * (le refresh token n'est utilisable qu'une fois). Émet le nouvel access token.
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

  /** Session irrécupérable (renouvellement refusé, CA-03-13) : purge locale puis redirection vers `/login`. */
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
