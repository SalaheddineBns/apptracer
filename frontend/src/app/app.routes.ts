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
        // RG-04-01 : vue équipe réservée au MANAGER (ni USER ni ADMIN).
        path: 'team',
        canActivate: [roleGuard(Role.Manager)],
        loadComponent: () => import('./features/team-view/pages/team-week').then((m) => m.TeamWeekPage),
      },
      {
        path: 'team/members/:userId',
        canActivate: [roleGuard(Role.Manager)],
        loadComponent: () => import('./features/team-view/pages/member-journal').then((m) => m.MemberJournalPage),
      },
      {
        path: 'admin/accounts',
        canActivate: [roleGuard(Role.Admin)],
        loadComponent: () => import('./features/accounts/pages/accounts').then((m) => m.AccountsPage),
      },
      {
        // RG-05-08 : gestion des équipes réservée à l'ADMIN.
        path: 'admin/teams',
        canActivate: [roleGuard(Role.Admin)],
        loadComponent: () => import('./features/teams/pages/teams').then((m) => m.TeamsPage),
      },
      {
        path: 'admin/activity-types',
        canActivate: [roleGuard(Role.Admin)],
        loadComponent: () => import('./features/referentials/pages/activity-types').then((m) => m.ActivityTypesPage),
      },
      {
        path: 'admin/projects',
        canActivate: [roleGuard(Role.Admin)],
        loadComponent: () => import('./features/referentials/pages/projects').then((m) => m.ProjectsPage),
      },
      {
        // RG-09-07 : gestion des fonctions réservée à l'ADMIN.
        path: 'admin/job-titles',
        canActivate: [roleGuard(Role.Admin)],
        loadComponent: () => import('./features/job-titles/pages/job-titles').then((m) => m.JobTitlesPage),
      },
      {
        path: 'admin/settings',
        canActivate: [roleGuard(Role.Admin)],
        loadComponent: () => import('./features/settings/pages/settings').then((m) => m.SettingsPage),
      },
      {
        path: 'account/password',
        loadComponent: () => import('./features/auth/pages/change-password').then((m) => m.ChangePasswordPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
