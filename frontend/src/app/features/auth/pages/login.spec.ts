import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { AuthenticationService, Role } from '../../../core/api/generated';
import { I18n } from '../../../core/i18n/i18n.service';
import { safeReturnUrl } from '../../../core/auth/return-url';
import { LoginPage } from './login';

@Component({ template: '' })
class Dummy {}

const tokens = (role: Role) => ({
  accessToken: 'a',
  refreshToken: 'r',
  user: { id: 1, email: 'a@b.fr', firstName: 'A', lastName: 'B', role },
});

const unauthorized = () =>
  throwError(
    () => new HttpErrorResponse({ status: 401, error: { status: 401, detail: 'Identifiants invalides' } }),
  );

describe('LoginPage', () => {
  const login = vi.fn();
  let harness: RouterTestingHarness;
  let root: HTMLElement;

  async function open(url = '/login'): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'login', component: LoginPage },
          { path: 'journal', component: Dummy },
          { path: 'journal/semaine', component: Dummy },
          { path: 'admin/accounts', component: Dummy },
        ]),
        { provide: AuthenticationService, useValue: { login } },
      ],
    });
    localStorage.clear();
    harness = await RouterTestingHarness.create(url);
    root = harness.routeNativeElement as HTMLElement;
  }

  const cy = (name: string) => root.querySelector<HTMLElement>(`[data-cy="${name}"]`);

  async function fill(email: string, password: string): Promise<void> {
    const emailInput = cy('champ-email') as HTMLInputElement;
    const passwordInput = cy('champ-mot-de-passe') as HTMLInputElement;
    emailInput.value = email;
    emailInput.dispatchEvent(new Event('input'));
    passwordInput.value = password;
    passwordInput.dispatchEvent(new Event('input'));
    await harness.fixture.whenStable();
  }

  async function submit(): Promise<void> {
    root.querySelector<HTMLButtonElement>('[data-cy="btn-se-connecter"] button')?.click();
    await harness.fixture.whenStable();
  }

  beforeEach(() => login.mockReset());

  it('RG-08-10, CA-08-02 : la page de connexion propose « Français » et « English », choisir « English » change la langue sans recharger ni vider la saisie', async () => {
    await open();
    await harness.fixture.whenStable();
    expect(cy('selecteur-langue')).not.toBeNull();
    expect(cy('choix-langue-fr')?.textContent).toContain('Français');
    expect(cy('choix-langue-en')?.textContent).toContain('English');
    expect(cy('selecteur-langue')?.querySelector('[aria-pressed="true"]')?.textContent).toContain('Français');

    await fill('a@b.fr', 'secret');
    cy('choix-langue-en')?.click();
    await harness.fixture.whenStable();

    expect(TestBed.inject(I18n).lang()).toBe('en');
    expect(document.documentElement.lang).toBe('en');
    expect(cy('selecteur-langue')?.querySelector('[aria-pressed="true"]')?.textContent).toContain('English');
    expect((cy('champ-email') as HTMLInputElement).value).toBe('a@b.fr');
    expect((cy('champ-mot-de-passe') as HTMLInputElement).value).toBe('secret');
  });

  it('CA-03-01 : connexion réussie, arrivée sur « Mon journal » pour un USER', async () => {
    login.mockReturnValue(of(tokens(Role.User)));
    await open();
    await fill('a@b.fr', 'x');
    await submit();
    expect(login).toHaveBeenCalledWith({ email: 'a@b.fr', password: 'x' }, 'body', false, expect.anything());
    expect(TestBed.inject(Router).url).toBe('/journal');
    expect(localStorage.getItem('accessToken')).toBe('a');
    expect(localStorage.getItem('refreshToken')).toBe('r');
  });

  it('CA-03-01 : redirige vers la page demandée (returnUrl interne) sinon vers l’accueil du rôle', async () => {
    login.mockReturnValue(of(tokens(Role.Manager)));
    await open('/login?returnUrl=%2Fjournal%2Fsemaine');
    await fill('a@b.fr', 'x');
    await submit();
    expect(TestBed.inject(Router).url).toBe('/journal/semaine');
  });

  it('CA-03-01 : un ADMIN arrive sur « Comptes » et un returnUrl externe est ignoré', async () => {
    login.mockReturnValue(of(tokens(Role.Admin)));
    await open('/login?returnUrl=https%3A%2F%2Fevil.example%2Fx');
    await fill('a@b.fr', 'x');
    await submit();
    expect(TestBed.inject(Router).url).toBe('/admin/accounts');
  });

  it.each([
    ['CA-03-02 : mot de passe incorrect'],
    ['CA-03-03 : e-mail inconnu'],
    ['CA-03-04 : compte inactif'],
  ])('%s, l’écran affiche « Identifiants invalides » sans quitter /login', async () => {
    login.mockReturnValue(unauthorized());
    await open();
    await fill('a@b.fr', 'mauvais');
    await submit();
    expect(cy('message-erreur-connexion')?.textContent).toContain('Identifiants invalides');
    expect(TestBed.inject(Router).url).toBe('/login');
    expect(localStorage.getItem('accessToken')).toBeNull();
  });

  it('bloque l’envoi et signale les champs obligatoires ou un e-mail invalide', async () => {
    await open();
    await submit();
    expect(login).not.toHaveBeenCalled();
    expect(root.querySelectorAll('[role="alert"]').length).toBe(2);

    await fill('pas-un-email', 'x');
    await submit();
    expect(login).not.toHaveBeenCalled();
    expect(root.textContent).toContain('adresse e-mail valide');
  });

  it('bouton afficher / masquer le mot de passe : libellé accessible, état reflété, sans soumission', async () => {
    await open();
    const input = cy('champ-mot-de-passe') as HTMLInputElement;
    const toggle = () => root.querySelector<HTMLButtonElement>('[data-cy="btn-afficher-mot-de-passe"] button') as HTMLButtonElement;
    expect(input.type).toBe('password');
    expect(toggle().type).toBe('button');
    expect(toggle().getAttribute('aria-label')).toBe('Afficher le mot de passe');

    toggle().click();
    await harness.fixture.whenStable();
    expect(input.type).toBe('text');
    expect(toggle().getAttribute('aria-label')).toBe('Masquer le mot de passe');
    expect(login).not.toHaveBeenCalled();
  });

  it('safeReturnUrl : refuse les URL externes et /login', () => {
    expect(safeReturnUrl('/journal?x=1')).toBe('/journal?x=1');
    for (const bad of ['https://evil.example', '//evil.example', '/\\evil', 'javascript:alert(1)', '/login', '/login?returnUrl=/x', null, 42]) {
      expect(safeReturnUrl(bad)).toBeNull();
    }
  });
});
