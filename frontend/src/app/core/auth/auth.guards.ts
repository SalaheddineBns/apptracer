import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../api/generated';
import { AuthStore } from './auth.store';
import { homeUrlFor } from './roles';

/** CA-03-05 : sans session, redirection vers `/login` en mémorisant la page demandée (`returnUrl`). */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  return auth.isAuthenticated() || router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Rôle insuffisant (hiérarchie RG-03-10) : redirection vers l'accueil du rôle. Le back reste la référence. */
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

/** Déjà connecté : `/login` renvoie vers l'accueil du rôle. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  const user = auth.user();
  return user ? router.createUrlTree([homeUrlFor(user.role)]) : true;
};
