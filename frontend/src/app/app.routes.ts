import { Routes } from '@angular/router';
import { Role } from './core/api/generated';
import { authGuard, guestGuard, roleGuard } from './core/auth/auth.guards';

// Les autres routes de l'architecture §7.2 (coquille, `/`, comptes, `**`) arrivent avec T-03-15 et T-03-17.
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/pages/login').then((m) => m.LoginPage),
  },
  {
    // Route provisoire, remplacée par la vraie page SFD-01 (T-01-11).
    path: 'journal',
    canActivate: [authGuard, roleGuard(Role.User)],
    loadComponent: () =>
      import('./features/time-entries/pages/journal').then((m) => m.JournalPlaceholderPage),
  },
];
