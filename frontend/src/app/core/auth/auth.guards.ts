import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../api/generated';
import { AuthStore } from './auth.store';
import { homeUrlFor } from './roles';

/** CA-03-05: without a session, redirect to `/login`, remembering the requested page (`returnUrl`). */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  return auth.isAuthenticated() || router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Insufficient role (RG-03-10 hierarchy): redirect to the role's home page. The back end remains the reference. */
export const roleGuard =
  (required: Role): CanActivateFn =>
  (_route, state) => {
    const auth = inject(AuthStore);
    const router = inject(Router);
    const user = auth.user();
    if (!user) {
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
    }
    return auth.hasRole(required) || router.createUrlTree([homeUrlFor(user.role)]);
  };

/** Already signed in: `/login` sends back to the role's home page. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  const user = auth.user();
  return user ? router.createUrlTree([homeUrlFor(user.role)]) : true;
};

/** Route `/`: redirect to the role's home page (under `authGuard`, the user is known). */
export const homeRedirectGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  const user = auth.user();
  return router.createUrlTree([user ? homeUrlFor(user.role) : '/login']);
};
