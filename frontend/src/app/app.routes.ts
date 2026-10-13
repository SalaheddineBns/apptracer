import { Routes } from '@angular/router';
import { Role } from './core/api/generated';
import { authGuard, guestGuard, homeRedirectGuard, roleGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/pages/login').then((m) => m.LoginPage),
  },
  {
    // Coquille (en-tête E2) : enveloppe toutes les pages protégées.
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./core/layout/shell').then((m) => m.Shell),
    children: [
      // `/` : redirection vers l'accueil du rôle (chaque accueil passe son propre roleGuard : pas de boucle).
      { path: '', pathMatch: 'full', canActivate: [homeRedirectGuard], children: [] },
      {
        // RG-01-14 : USER et MANAGER (un MANAGER satisfait Role.User).
        path: 'journal',
        canActivate: [roleGuard(Role.User)],
        loadComponent: () => import('./features/time-entries/pages/journal').then((m) => m.JournalPage),
      },
      {
        path: 'admin/comptes',
        canActivate: [roleGuard(Role.Admin)],
        loadComponent: () => import('./features/accounts/pages/accounts').then((m) => m.AccountsPage),
      },
      {
        path: 'admin/types-activite',
        canActivate: [roleGuard(Role.Admin)],
        loadComponent: () => import('./features/referentials/pages/activity-types').then((m) => m.ActivityTypesPage),
      },
      {
        path: 'admin/projets',
        canActivate: [roleGuard(Role.Admin)],
        loadComponent: () => import('./features/referentials/pages/projects').then((m) => m.ProjectsPage),
      },
      {
        path: 'admin/parametres',
        canActivate: [roleGuard(Role.Admin)],
        loadComponent: () => import('./features/settings/pages/settings').then((m) => m.SettingsPage),
      },
      {
        path: 'compte/mot-de-passe',
        loadComponent: () => import('./features/auth/pages/change-password').then((m) => m.ChangePasswordPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
