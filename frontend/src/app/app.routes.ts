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
    // Shell (header E2): wraps every protected page.
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./core/layout/shell').then((m) => m.Shell),
    children: [
      // `/`: redirect to the role's home page (each home page has its own roleGuard: no loop).
      { path: '', pathMatch: 'full', canActivate: [homeRedirectGuard], children: [] },
      {
        // RG-01-14: USER and MANAGER (a MANAGER satisfies Role.User).
        path: 'journal',
        canActivate: [roleGuard(Role.User)],
        loadComponent: () => import('./features/time-entries/pages/journal').then((m) => m.JournalPage),
      },
      {
        path: 'projects',
        canActivate: [roleGuard(Role.User)],
        loadComponent: () => import('./features/my-projects/pages/my-projects').then((m) => m.MyProjectsPage),
      },
      {
        // RG-04-01: team view reserved for the MANAGER (neither USER nor ADMIN).
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
        // RG-05-08: team management reserved for the ADMIN.
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
        // RG-09-07: job title management reserved for the ADMIN.
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
