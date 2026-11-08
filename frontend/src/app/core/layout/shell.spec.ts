import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { routes } from '../../app.routes';
import { ActivityTypesService, AppSettingsService, AuthenticationService, ProjectsService, Role, TeamsService, TeamViewService, UsersService } from '../api/generated';
import { AuthStore } from '../auth/auth.store';

const user = (role: Role) => ({ id: 1, email: 'ada@x.fr', firstName: 'Ada', lastName: 'Martin', role });

describe('coquille applicative (E2) et routes', () => {
  const getCurrentUser = vi.fn();
  const logout = vi.fn();
  const listUsers = vi.fn();
  const listActivityTypes = vi.fn();
  const listProjects = vi.fn();
  const listTeams = vi.fn();
  const listMyManagedTeams = vi.fn();
  const getAppSettings = vi.fn();
  const updateAppSettings = vi.fn();
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
        { provide: ActivityTypesService, useValue: { listActivityTypes } },
        { provide: ProjectsService, useValue: { listProjects } },
        { provide: TeamsService, useValue: { listTeams } },
        { provide: TeamViewService, useValue: { listMyManagedTeams } },
        { provide: AppSettingsService, useValue: { getAppSettings, updateAppSettings } },
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
    listActivityTypes.mockReset();
    listActivityTypes.mockReturnValue(of([]));
    listProjects.mockReset();
    listProjects.mockReturnValue(of([]));
    listTeams.mockReset();
    listTeams.mockReturnValue(of([]));
    listMyManagedTeams.mockReset();
    listMyManagedTeams.mockReturnValue(of([]));
    getAppSettings.mockReset();
    getAppSettings.mockReturnValue(of({ standardDayMinutes: 480, version: 0, lastModifiedAt: null }));
    updateAppSettings.mockReset();
    listUsers.mockReturnValue(of({ content: [], page: 0, size: 20, totalElements: 0 }));
  });

  it.each([
    [Role.User, '/journal', true, false],
    [Role.Manager, '/journal', true, false],
    [Role.Admin, '/admin/accounts', false, true],
  ])('RG-03-10 : %s arrive sur %s, navigation journal=%s comptes=%s', async (role, home, journal, accounts) => {
    await start(role);
    await harness.navigateByUrl('/');
    await harness.fixture.whenStable();
    expect(url()).toBe(home);
    expect(cy('nom-utilisateur')?.textContent).toContain('Ada Martin');
    expect(!!cy('nav-mon-journal')).toBe(journal);
    expect(!!cy('nav-comptes')).toBe(accounts);
  });

  it('CA-03-09 : le menu « Comptes » n’est pas affiché pour un USER, et /admin/accounts le renvoie vers son accueil', async () => {
    await start(Role.User);
    await harness.navigateByUrl('/journal');
    expect(cy('nav-comptes')).toBeNull();
    await harness.navigateByUrl('/admin/accounts');
    expect(url()).toBe('/journal');
    expect(listUsers).not.toHaveBeenCalled();
  });

  it.each([
    [Role.User, '/journal', false],
    [Role.Manager, '/journal', false],
    [Role.Admin, '/admin/accounts', true],
  ])('CA-02-09 : %s, entrée « Types d’activité » affichée=%s, et la route renvoie les non-ADMIN vers leur accueil sans appel API', async (role, home, shown) => {
    await start(role);
    await harness.navigateByUrl(home);
    expect(!!cy('nav-types-activite')).toBe(shown);
    await harness.navigateByUrl('/admin/activity-types');
    await harness.fixture.whenStable();
    expect(url()).toBe(shown ? '/admin/activity-types' : home);
    expect(listActivityTypes).toHaveBeenCalledTimes(shown ? 1 : 0);
  });

  it.each([
    [Role.User, '/journal', false],
    [Role.Manager, '/journal', false],
    [Role.Admin, '/admin/accounts', true],
  ])('CA-02-09 : %s, entrée « Projets » affichée=%s, et la route renvoie les non-ADMIN vers leur accueil sans appel API', async (role, home, shown) => {
    await start(role);
    await harness.navigateByUrl(home);
    expect(!!cy('nav-projets')).toBe(shown);
    await harness.navigateByUrl('/admin/projects');
    await harness.fixture.whenStable();
    expect(url()).toBe(shown ? '/admin/projects' : home);
    expect(listProjects).toHaveBeenCalledTimes(shown ? 1 : 0);
  });

  it.each([
    [Role.User, '/journal', false],
    [Role.Manager, '/journal', false],
    [Role.Admin, '/admin/accounts', true],
  ])('CA-06-07 : %s, entrée « Paramètres » affichée=%s, et la route renvoie les non-ADMIN vers leur accueil sans appel API', async (role, home, shown) => {
    await start(role);
    await harness.navigateByUrl(home);
    expect(!!cy('nav-parametres')).toBe(shown);
    await harness.navigateByUrl('/admin/settings');
    await harness.fixture.whenStable();
    expect(url()).toBe(shown ? '/admin/settings' : home);
    expect(getAppSettings).toHaveBeenCalledTimes(shown ? 1 : 0);
    expect(updateAppSettings).not.toHaveBeenCalled();
  });

  it.each([
    [Role.User, '/journal', false],
    [Role.Manager, '/journal', false],
    [Role.Admin, '/admin/accounts', true],
  ])('CA-05-11 : %s, entrée « Équipes » affichée=%s, et la route renvoie les non-ADMIN vers leur accueil sans appel API', async (role, home, shown) => {
    await start(role);
    await harness.navigateByUrl(home);
    expect(!!cy('nav-equipes')).toBe(shown);
    await harness.navigateByUrl('/admin/teams');
    await harness.fixture.whenStable();
    expect(url()).toBe(shown ? '/admin/teams' : home);
    expect(listTeams).toHaveBeenCalledTimes(shown ? 1 : 0);
  });

  it.each([
    [Role.User, '/journal', false],
    [Role.Manager, '/journal', true],
    [Role.Admin, '/admin/accounts', false],
  ])('CA-04-05, CA-04-06 : %s, entrée « Mon équipe » affichée=%s, et la route renvoie les non-MANAGER vers leur accueil sans appel API', async (role, home, shown) => {
    await start(role);
    await harness.navigateByUrl(home);
    expect(!!cy('nav-mon-equipe')).toBe(shown);
    await harness.navigateByUrl('/team');
    await harness.fixture.whenStable();
    expect(url()).toBe(shown ? '/team' : home);
    expect(listMyManagedTeams).toHaveBeenCalledTimes(shown ? 1 : 0);
  });

  it('l’entrée « Mon équipe » suit « Mon journal » pour un MANAGER', async () => {
    await start(Role.Manager);
    await harness.navigateByUrl('/journal');
    const order = Array.from(document.body.querySelectorAll('nav [data-cy^="nav-"]')).map((a) => a.getAttribute('data-cy'));
    expect(order).toEqual(['nav-mon-journal', 'nav-mon-equipe']);
  });

  it('les entrées ADMIN sont dans l’ordre de l’architecture : Comptes, Équipes, Types d’activité, Projets, Paramètres', async () => {
    await start(Role.Admin);
    await harness.navigateByUrl('/admin/accounts');
    const order = Array.from(document.body.querySelectorAll('nav [data-cy^="nav-"]')).map((a) => a.getAttribute('data-cy'));
    expect(order).toEqual(['nav-comptes', 'nav-equipes', 'nav-types-activite', 'nav-projets', 'nav-parametres']);
  });

  it('Échap annule toute confirmation sans appeler accept, la croix est nommée « Fermer » et le focus revient au déclencheur', async () => {
    await start(Role.Admin);
    await harness.navigateByUrl('/admin/accounts');
    const opener = cy('nav-comptes') as HTMLElement;
    opener.focus();
    expect(document.activeElement).toBe(opener);
    const accept = vi.fn();
    const reject = vi.fn();
    TestBed.inject(ConfirmationService).confirm({ header: 'Titre', message: 'Message', accept, reject, acceptLabel: 'OK', rejectLabel: 'Annuler' });
    await harness.fixture.whenStable();
    expect(cy('btn-confirmer')).not.toBeNull();
    expect(document.body.querySelector('.p-dialog-header button')?.getAttribute('aria-label')).toBe('Fermer');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', keyCode: 27, bubbles: true }));
    await harness.fixture.whenStable();
    await vi.waitFor(() => expect(cy('btn-confirmer')).toBeNull());
    expect(accept).not.toHaveBeenCalled();
    await vi.waitFor(() => expect(document.activeElement).toBe(opener));
  });

  it('F2 : le message de la confirmation s’affiche comme du texte, jamais interprété comme du HTML', async () => {
    await start(Role.Admin);
    await harness.navigateByUrl('/admin/accounts');
    const message = '<u>Souligné</u> <img src=x> <b>gras</b>';
    TestBed.inject(ConfirmationService).confirm({ header: 'Titre', message, acceptLabel: 'OK', rejectLabel: 'Annuler' });
    await harness.fixture.whenStable();
    const content = document.body.querySelector('.p-confirmdialog-message') as HTMLElement;
    expect(content.textContent).toContain(message);
    expect(content.querySelector('u, img, b')).toBeNull();
  });

  it('confirmation : 32rem (95vw au plus) et focus d’ouverture sur « Annuler », pas sur l’action destructrice', async () => {
    await start(Role.Admin);
    await harness.navigateByUrl('/admin/accounts');
    TestBed.inject(ConfirmationService).confirm({ header: 'Titre', message: 'Message', acceptLabel: 'Désactiver', rejectLabel: 'Annuler' });
    await harness.fixture.whenStable();
    const dialog = document.body.querySelector('.p-dialog') as HTMLElement;
    expect(dialog.classList).toContain('w-[32rem]');
    expect(dialog.classList).toContain('max-w-[95vw]');
    await vi.waitFor(() => expect(document.activeElement).toBe(cy('btn-annuler')));
  });

  it('confirmation confirmée : le focus va au bouton qui remplace celui d’origine (« Désactiver » devient « Réactiver »)', async () => {
    await start(Role.Admin);
    await harness.navigateByUrl('/admin/accounts');
    const row = document.createElement('table');
    row.innerHTML = '<tbody><tr data-cy="ligne-test"><td><button type="button">Modifier</button><button type="button" id="origine">Désactiver</button></td></tr></tbody>';
    document.body.appendChild(row);
    const origin = row.querySelector('#origine') as HTMLButtonElement;
    origin.focus();
    const swap = () => {
      // Comme la page : bouton désactivé pendant l'appel, puis remplacé à la réponse.
      origin.disabled = true;
      setTimeout(() => {
        const replacement = document.createElement('button');
        replacement.id = 'remplacant';
        replacement.textContent = 'Réactiver';
        origin.replaceWith(replacement);
      }, 50);
    };
    TestBed.inject(ConfirmationService).confirm({ header: 'Titre', message: 'Message', acceptLabel: 'Désactiver', rejectLabel: 'Annuler', accept: swap });
    await harness.fixture.whenStable();
    cy('btn-confirmer')?.click();
    await harness.fixture.whenStable();
    await vi.waitFor(() => expect(document.activeElement?.id).toBe('remplacant'));
    row.remove();
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

  it.each([
    [Role.Manager, '/equipe', '/journal'],
    [Role.Manager, '/equipe/membres/10?semaine=2026-10-05', '/journal'],
    [Role.User, '/compte/mot-de-passe', '/journal'],
    [Role.Admin, '/admin/comptes', '/admin/accounts'],
    [Role.Admin, '/admin/types-activite', '/admin/accounts'],
  ])('RG-08-09 : l’ancienne adresse française %s %s tombe sur la route « ** » (accueil du rôle)', async (role, oldUrl, home) => {
    await start(role);
    await harness.navigateByUrl(oldUrl);
    await harness.fixture.whenStable();
    expect(url()).toBe(home);
    expect(listMyManagedTeams).not.toHaveBeenCalled();
    expect(listActivityTypes).not.toHaveBeenCalled();
  });

  it.each([
    [Role.User, '/account/password'],
    [Role.Manager, '/team'],
    [Role.Admin, '/admin/settings'],
  ])('RG-08-09 : l’adresse anglaise %s %s est servie telle quelle', async (role, address) => {
    await start(role);
    await harness.navigateByUrl(address);
    await harness.fixture.whenStable();
    expect(url()).toBe(address);
  });

  it('routes : une URL inconnue renvoie un ADMIN vers /admin/accounts', async () => {
    await start(Role.Admin);
    await harness.navigateByUrl('/n-importe-quoi');
    expect(url()).toBe('/admin/accounts');
    expect(el()).toBeTruthy();
  });
});
