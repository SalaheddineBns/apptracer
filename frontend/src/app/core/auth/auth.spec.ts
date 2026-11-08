import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { provideApiBasePath } from '../api/api.config';
import { Role } from '../api/generated';
import { errorInterceptor } from '../errors/error.interceptor';
import { authGuard, guestGuard, roleGuard } from './auth.guards';
import { authInterceptor } from './auth.interceptor';
import { AuthStore } from './auth.store';

@Component({ template: '' })
class Dummy {}

const user = (role: Role) => ({ id: 1, email: 'a@b.fr', firstName: 'A', lastName: 'B', role });

describe('authentification (socle front)', () => {
  const toast = vi.fn();
  let http: HttpClient;
  let controller: HttpTestingController;
  let router: Router;
  let auth: AuthStore;

  beforeEach(async () => {
    localStorage.clear();
    toast.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
        provideHttpClientTesting(),
        provideApiBasePath(),
        provideRouter([
          { path: 'login', component: Dummy, canActivate: [guestGuard] },
          { path: 'journal', component: Dummy, canActivate: [authGuard, roleGuard(Role.User)] },
          { path: 'admin/accounts', component: Dummy, canActivate: [authGuard, roleGuard(Role.Admin)] },
        ]),
        { provide: MessageService, useValue: { add: toast } },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    auth = TestBed.inject(AuthStore);
  });

  async function startSession(role: Role): Promise<void> {
    localStorage.setItem('accessToken', 'old');
    localStorage.setItem('refreshToken', 'r1');
    const init = auth.init();
    controller.expectOne('/api/v1/auth/me').flush(user(role));
    await init;
  }

  it('CA-03-05 : redirige un utilisateur non connecté vers /login en mémorisant la page demandée', async () => {
    await auth.init();
    await router.navigateByUrl('/journal');
    expect(router.url).toBe('/login?returnUrl=%2Fjournal');
  });

  it('CA-03-06 : renouvelle la session sur 401, rejoue la requête sans toast, un seul refresh pour plusieurs requêtes', async () => {
    await startSession(Role.User);
    const results: string[] = [];
    http.get<string>('/api/v1/a').subscribe((r) => results.push(r));
    http.get<string>('/api/v1/b').subscribe((r) => results.push(r));
    controller.expectOne('/api/v1/a').flush({}, { status: 401, statusText: 'x' });
    controller.expectOne('/api/v1/b').flush({}, { status: 401, statusText: 'x' });

    const refresh = controller.expectOne('/api/v1/auth/refresh');
    expect(refresh.request.body).toEqual({ refreshToken: 'r1' });
    expect(refresh.request.headers.has('Authorization')).toBe(false);
    refresh.flush({ accessToken: 'new', refreshToken: 'r2', user: user(Role.User) });

    for (const url of ['/api/v1/a', '/api/v1/b']) {
      const replay = controller.expectOne(url);
      expect(replay.request.headers.get('Authorization')).toBe('Bearer new');
      replay.flush(url);
    }
    expect(results).toEqual(['/api/v1/a', '/api/v1/b']);
    expect(localStorage.getItem('refreshToken')).toBe('r2');
    expect(toast).not.toHaveBeenCalled();
  });

  it('CA-03-13 : purge la session et redirige vers /login si le renouvellement échoue', async () => {
    await startSession(Role.User);
    await router.navigateByUrl('/journal');
    let failed = false;
    http.get('/api/v1/a').subscribe({ error: () => (failed = true) });
    controller.expectOne('/api/v1/a').flush({}, { status: 401, statusText: 'x' });
    controller.expectOne('/api/v1/auth/refresh').flush({}, { status: 401, statusText: 'x' });
    await new Promise((r) => setTimeout(r));

    expect(failed).toBe(true);
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
    expect(router.url).toContain('/login');
    expect(toast).not.toHaveBeenCalled();
  });

  it('un 401 sur login ne déclenche aucun renouvellement', async () => {
    await auth.init();
    let failed = false;
    auth.login('a@b.fr', 'x').subscribe({ error: () => (failed = true) });
    controller.expectOne('/api/v1/auth/login').flush({}, { status: 401, statusText: 'x' });
    controller.expectNone('/api/v1/auth/refresh');
    expect(failed).toBe(true);
  });

  it('RG-03-10 : MANAGER satisfait USER, ADMIN ne satisfait ni USER ni MANAGER', async () => {
    await startSession(Role.Manager);
    expect(auth.hasRole(Role.User)).toBe(true);
    expect(auth.hasRole(Role.Admin)).toBe(false);
  });

  it('RG-03-10 : un ADMIN est refusé sur /journal et renvoyé vers son accueil', async () => {
    await startSession(Role.Admin);
    expect(auth.hasRole(Role.User)).toBe(false);
    expect(auth.hasRole(Role.Manager)).toBe(false);
    await router.navigateByUrl('/journal');
    expect(router.url).toBe('/admin/accounts');
  });

  it('guestGuard : un utilisateur connecté qui ouvre /login est renvoyé vers son accueil', async () => {
    await startSession(Role.Manager);
    await router.navigateByUrl('/login');
    expect(router.url).toBe('/journal');
  });

  it('logout : révoque le refresh token, purge les jetons et redirige vers /login (CA-03-08)', async () => {
    await startSession(Role.User);
    auth.logout().subscribe();
    const req = controller.expectOne('/api/v1/auth/logout');
    expect(req.request.body).toEqual({ refreshToken: 'r1' });
    req.flush(null);
    await new Promise((r) => setTimeout(r));
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(router.url).toBe('/login');
  });
});
