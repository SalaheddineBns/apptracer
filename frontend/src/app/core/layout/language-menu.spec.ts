import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { routes } from '../../app.routes';
import {
  ActivityTypesService,
  AppSettingsService,
  AuthenticationService,
  ProjectsService,
  Role,
  TimeEntriesService,
} from '../api/generated';
import { AuthStore } from '../auth/auth.store';
import { I18n } from '../i18n/i18n.service';

describe('SFD-08 E1 : sélecteur de langue dans le menu utilisateur', () => {
  let harness: RouterTestingHarness;

  async function start(): Promise<void> {
    localStorage.clear();
    localStorage.setItem('accessToken', 'a');
    localStorage.setItem('refreshToken', 'r');
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        MessageService,
        ConfirmationService,
        {
          provide: AuthenticationService,
          useValue: {
            getCurrentUser: () => of({ id: 1, email: 'ada@x.fr', firstName: 'Ada', lastName: 'Martin', role: Role.User }),
            logout: () => of(null),
          },
        },
        {
          provide: TimeEntriesService,
          useValue: {
            listMyTimeEntries: () =>
              of({ weekStart: '2026-10-05', weekEnd: '2026-10-11', totalMinutes: 0, days: [], entries: [] }),
          },
        },
        { provide: ActivityTypesService, useValue: { listActivityTypes: () => of([]) } },
        { provide: ProjectsService, useValue: { listProjects: () => of([]) } },
        { provide: AppSettingsService, useValue: { getAppSettings: () => of({ standardDayMinutes: 480, version: 0 }) } },
      ],
    });
    await TestBed.inject(AuthStore).init();
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/journal?week=2026-10-05');
    await harness.fixture.whenStable();
  }

  const cy = (name: string) => document.body.querySelector<HTMLElement>(`[data-cy="${name}"]`);

  async function openMenu(): Promise<void> {
    cy('btn-menu-utilisateur')?.querySelector('button')?.click();
    await harness.fixture.whenStable();
  }

  it('RG-08-04 : le menu propose « Français » et « English » à côté de « Changer mon mot de passe », la langue en vigueur est signalée', async () => {
    await start();
    await openMenu();
    expect(cy('menu-changer-mot-de-passe')).not.toBeNull();
    expect(cy('menu-langue')).not.toBeNull();
    expect(cy('choix-langue-fr')?.textContent).toContain('Français');
    expect(cy('choix-langue-en')?.textContent).toContain('English');
  });

  it('RG-08-04 : les deux langues sont des menuitemradio avec aria-checked, regroupées sous un groupe nommé par « Langue de l’interface »', async () => {
    await start();
    await openMenu();
    const fr = cy('choix-langue-fr')?.closest('li') as HTMLElement;
    const en = cy('choix-langue-en')?.closest('li') as HTMLElement;
    expect(fr.getAttribute('role')).toBe('menuitemradio');
    expect(en.getAttribute('role')).toBe('menuitemradio');
    expect(fr.getAttribute('aria-checked')).toBe('true');
    expect(en.getAttribute('aria-checked')).toBe('false');
    expect(fr.getAttribute('lang')).toBe('fr');
    expect(en.getAttribute('lang')).toBe('en');

    const group = cy('menu-langue')?.closest('li') as HTMLElement;
    expect(group.getAttribute('role')).toBe('group');
    expect(document.getElementById(group.getAttribute('aria-labelledby') ?? '')?.textContent).toContain('Langue de l’interface');
    expect(group.getAttribute('aria-owns')?.split(' ')).toEqual([fr.id, en.id]);

    cy('choix-langue-en')?.click();
    await harness.fixture.whenStable();
    if (!cy('choix-langue-en')) {
      await openMenu();
    }
    expect((cy('choix-langue-en')?.closest('li') as HTMLElement).getAttribute('aria-checked')).toBe('true');
    expect((cy('choix-langue-fr')?.closest('li') as HTMLElement).getAttribute('aria-checked')).toBe('false');
  });

  it('CA-08-03 : choisir « English » traduit les jours et conserve la saisie du dialogue ouvert, sans rechargement', async () => {
    await start();
    expect(cy('jour-2026-10-05')?.querySelector('h2')?.textContent).toContain('lundi');

    cy('btn-nouvelle-saisie')?.querySelector('button')?.click();
    await harness.fixture.whenStable();
    const comment = cy('champ-commentaire') as HTMLTextAreaElement;
    comment.value = 'Revue de la spécification';
    comment.dispatchEvent(new Event('input'));
    await harness.fixture.whenStable();

    await openMenu();
    cy('choix-langue-en')?.click();
    await harness.fixture.whenStable();

    expect(TestBed.inject(I18n).lang()).toBe('en');
    expect(cy('jour-2026-10-05')?.querySelector('h2')?.textContent).toContain('Monday');
    expect(cy('jour-2026-10-11')?.querySelector('h2')?.textContent).toContain('Sunday');
    expect(cy('dialogue-saisie')).not.toBeNull();
    expect((cy('champ-commentaire') as HTMLTextAreaElement).value).toBe('Revue de la spécification');
  });

  it('CA-08-04 : la langue choisie reste après un nouveau démarrage (rechargement, reconnexion) sur le même navigateur', async () => {
    await start();
    await openMenu();
    cy('choix-langue-en')?.click();
    await harness.fixture.whenStable();
    expect(localStorage.getItem('lang')).toBe('en');

    // Sign-out: only the session keys are cleared, not the language (RG-08-03).
    await openMenu();
    cy('menu-se-deconnecter')?.click();
    await harness.fixture.whenStable();
    await new Promise((r) => setTimeout(r));
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(localStorage.getItem('lang')).toBe('en');
    TestBed.resetTestingModule();
    expect(TestBed.inject(I18n).lang()).toBe('en');
  });
});
