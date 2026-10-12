import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { routes } from '../../app.routes';
import { AuthenticationService, Role, UsersService } from '../api/generated';
import { AuthStore } from '../auth/auth.store';

const user = (role: Role) => ({ id: 1, email: 'ada@x.fr', firstName: 'Ada', lastName: 'Martin', role });

describe('coquille applicative (E2) et routes', () => {
  const getCurrentUser = vi.fn();
  const logout = vi.fn();
  const listUsers = vi.fn();
  let harness: RouterTestingHarness;

  async function start(role: Role | null): Promise<void> {
    localStorage.clear();
    if (role) {
      localStorage.setItem('accessToken', 'a');
      localStorage.setItem('refreshToken', 'r');
      getCurrentUser.mockReturnValue(of(user(role)));
    }
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        MessageService,
        ConfirmationService,
        { provide: AuthenticationService, useValue: { getCurrentUser, logout } },
        { provide: UsersService, useValue: { listUsers } },
      ],
    });
    await TestBed.inject(AuthStore).init();
    harness = await RouterTestingHarness.create();
  }

  const el = () => harness.routeNativeElement as HTMLElement;
  const cy = (name: string) => document.body.querySelector<HTMLElement>(`[data-cy="${name}"]`);
  const url = () => TestBed.inject(Router).url;

  beforeEach(() => {
    getCurrentUser.mockReset();
    logout.mockReset();
    listUsers.mockReset();
    listUsers.mockReturnValue(of({ content: [], page: 0, size: 20, totalElements: 0 }));
  });

  it.each([
    [Role.User, '/journal', true, false],
    [Role.Manager, '/journal', true, false],
    [Role.Admin, '/admin/comptes', false, true],
  ])('RG-03-10 : %s arrive sur %s, navigation journal=%s comptes=%s', async (role, home, journal, accounts) => {
    await start(role);
    await harness.navigateByUrl('/');
    await harness.fixture.whenStable();
    expect(url()).toBe(home);
    expect(cy('nom-utilisateur')?.textContent).toContain('Ada Martin');
    expect(!!cy('nav-mon-journal')).toBe(journal);
    expect(!!cy('nav-comptes')).toBe(accounts);
  });

  it('CA-03-09 : le menu « Comptes » n’est pas affiché pour un USER, et /admin/comptes le renvoie vers son accueil', async () => {
    await start(Role.User);
    await harness.navigateByUrl('/journal');
    expect(cy('nav-comptes')).toBeNull();
    await harness.navigateByUrl('/admin/comptes');
    expect(url()).toBe('/journal');
    expect(listUsers).not.toHaveBeenCalled();
  });

  it('CA-03-08 : « Se déconnecter » révoque le refresh token et redirige vers /login', async () => {
    logout.mockReturnValue(of(null));
    await start(Role.User);
    await harness.navigateByUrl('/journal');
    cy('btn-menu-utilisateur')?.querySelector('button')?.click();
    await harness.fixture.whenStable();
    expect(cy('menu-changer-mot-de-passe')?.textContent).toContain('Changer mon mot de passe');
    cy('menu-se-deconnecter')?.click();
    await harness.fixture.whenStable();
    await new Promise((r) => setTimeout(r));
    expect(logout).toHaveBeenCalledWith({ refreshToken: 'r' }, 'body', false, expect.anything());
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(url()).toBe('/login');
  });

  it('routes : visiteur redirigé vers /login, URL inconnue renvoyée vers l’accueil, sans boucle', async () => {
    await start(null);
    await harness.navigateByUrl('/n-importe-quoi');
    expect(url()).toMatch(/^\/login/);
    await harness.navigateByUrl('/');
    expect(url()).toMatch(/^\/login/);
  });

  it('routes : une URL inconnue renvoie un ADMIN vers /admin/comptes', async () => {
    await start(Role.Admin);
    await harness.navigateByUrl('/n-importe-quoi');
    expect(url()).toBe('/admin/comptes');
    expect(el()).toBeTruthy();
  });
});
