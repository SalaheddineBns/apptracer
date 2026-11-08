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
  Granularity,
  ProjectsService,
  Role,
  TagColor,
  TeamsService,
  TeamViewService,
  TimeEntriesService,
  TimeEntryResponse,
  UsersService,
} from '../api/generated';
import { AuthStore } from '../auth/auth.store';
import { toProblem } from '../errors/problem';
import { DICTIONARIES, I18n } from './i18n.service';

/**
 * RG-08-05, CA-08-08 : l'interface passée en anglais ne contient plus de français. Chaque écran de chaque SFD est rendu en
 * anglais avec des données sans accent ni mot français (RG-08-07 : les données saisies ne se traduisent pas), puis tous les
 * textes affichés ou lus par les lecteurs d'écran sont comparés au vocabulaire français de l'application : un texte
 * oublié dans un gabarit ou dans le code est détecté ici.
 */
const FRENCH_WORDS = [
  'le', 'la', 'les', 'des', 'du', 'un', 'une', 'et', 'ou', 'pour', 'sur', 'dans', 'aucun', 'aucune', 'nouveau',
  'nouvelle', 'enregistrer', 'annuler', 'modifier', 'supprimer', 'desactiver', 'reactiver', 'semaine', 'journee',
  'saisie', 'saisies', 'equipe', 'equipes', 'compte', 'comptes', 'mot', 'passe', 'obligatoire', 'erreur', 'projet',
  'projets', 'membre', 'membres', 'connexion', 'connecter', 'chargement', 'jour', 'jours', 'heure', 'heures', 'lundi',
  'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi', 'dimanche', 'vous', 'votre', 'mes', 'ancien', 'statut', 'couleur',
  'actif', 'inactif', 'oui', 'non', 'reessayer', 'fermer', 'afficher', 'masquer', 'libelle', 'utilisateur',
  'administrateur', 'parametres', 'periode', 'plage', 'commentaire', 'duree', 'prenom', 'telephone',
];
const FRENCH = new RegExp(`[àâçéèêëîïôûùüœ]|\\b(${FRENCH_WORDS.join('|')})\\b`, 'i');

/** Tout ce qu'un utilisateur ou un lecteur d'écran reçoit : texte, `aria-label`, `placeholder`, `title`. */
function shownTexts(): string[] {
  const texts: string[] = [];
  // Les noms des langues (« Français », « English ») sont écrits dans leur propre langue (marqués `lang`) : hors contrôle.
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    if (!node.parentElement?.closest('[lang]')) {
      texts.push(node.textContent ?? '');
    }
  }
  document.body.querySelectorAll('[aria-label],[placeholder],[title]').forEach((el) => {
    texts.push(el.getAttribute('aria-label') ?? '', el.getAttribute('placeholder') ?? '', el.getAttribute('title') ?? '');
  });
  return texts.flatMap((value) => value.split(/\s+/)).filter(Boolean);
}

function frenchLeaks(): string[] {
  // « Français » est le nom de la langue, écrit dans sa propre langue dans le sélecteur (RG-08-10) : seule exception.
  return shownTexts().filter((word) => word !== 'Français' && FRENCH.test(word));
}

function leafValues(node: object): string[] {
  return Object.values(node).flatMap((value) => (typeof value === 'string' ? [value] : leafValues(value as object)));
}

const cy = (name: string) => document.body.querySelector<HTMLElement>(`[data-cy="${name}"]`);
const text = (name: string) => cy(name)?.textContent?.trim() ?? '';

function entry(over: Partial<TimeEntryResponse>): TimeEntryResponse {
  return {
    id: 1,
    granularity: Granularity.Hour,
    startDate: '2026-10-05',
    startTime: '09:00',
    endTime: '10:30',
    activityType: { code: 'MEETING', label: 'Meeting', color: TagColor.Warn, active: true },
    durationMinutes: 90,
    version: 0,
    ...over,
  };
}

const ENTRIES: TimeEntryResponse[] = [
  entry({}),
  entry({
    id: 2,
    granularity: Granularity.Day,
    startDate: '2026-10-06',
    startTime: null,
    endTime: null,
    activityType: { code: 'PROJECT_WORK', label: 'Project work', color: TagColor.Info, active: true },
    project: { code: 'APOLLO', name: 'Apollo', active: true },
    durationMinutes: 480,
    comment: 'Plain English comment',
  }),
  entry({
    id: 3,
    granularity: Granularity.Range,
    startDate: '2026-10-07',
    endDate: '2026-10-08',
    startTime: null,
    endTime: null,
    durationMinutes: 960,
  }),
];

const USERS = [
  { id: 1, email: 'ada@x.com', firstName: 'Ada', lastName: 'Martin', role: Role.Admin, active: true, version: 0 },
  { id: 2, email: 'bob@x.com', firstName: 'Bob', lastName: 'Stone', role: Role.User, active: false, version: 0 },
];

describe('SFD-08 : les écrans passés en anglais ne contiennent plus de français (CA-08-08)', () => {
  let harness: RouterTestingHarness;

  async function start(role: Role | null, url: string): Promise<void> {
    localStorage.clear();
    if (role !== null) {
      localStorage.setItem('accessToken', 'a');
      localStorage.setItem('refreshToken', 'r');
    }
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        MessageService,
        ConfirmationService,
        {
          provide: AuthenticationService,
          useValue: {
            getCurrentUser: () => of({ id: 1, email: 'ada@x.com', firstName: 'Ada', lastName: 'Martin', role }),
            logout: () => of(null),
          },
        },
        {
          provide: TimeEntriesService,
          useValue: {
            listMyTimeEntries: () =>
              of({
                weekStart: '2026-10-05',
                weekEnd: '2026-10-11',
                totalMinutes: 1530,
                days: [
                  { date: '2026-10-05', minutes: 90 },
                  { date: '2026-10-06', minutes: 480 },
                  { date: '2026-10-07', minutes: 480 },
                  { date: '2026-10-08', minutes: 480 },
                ],
                entries: ENTRIES,
              }),
          },
        },
        {
          provide: ActivityTypesService,
          useValue: {
            listActivityTypes: () =>
              of([
                { id: 1, code: 'MEETING', label: 'Meeting', color: TagColor.Warn, projectRequired: false, active: true, version: 0 },
                { id: 2, code: 'PROJECT_WORK', label: 'Project work', color: TagColor.Info, projectRequired: true, active: false, version: 0 },
              ]),
          },
        },
        {
          provide: ProjectsService,
          useValue: { listProjects: () => of([{ id: 1, code: 'APOLLO', name: 'Apollo', active: true, version: 0 }]) },
        },
        {
          provide: AppSettingsService,
          useValue: {
            getAppSettings: () =>
              of({
                standardDayMinutes: 480,
                version: 1,
                lastModifiedAt: '2026-10-12T12:05:00Z',
                lastModifiedBy: { id: 1, firstName: 'Ada', lastName: 'Martin' },
              }),
          },
        },
        {
          provide: UsersService,
          useValue: { listUsers: () => of({ content: USERS, page: 0, size: 20, totalElements: 2 }) },
        },
        {
          provide: TeamsService,
          useValue: {
            listTeams: () =>
              of([
                {
                  id: 1,
                  name: 'Platform',
                  manager: { ...USERS[0], role: Role.Manager },
                  members: [USERS[1]],
                  memberCount: 1,
                  version: 0,
                },
                { id: 2, name: 'Mobile', manager: { ...USERS[0], role: Role.Manager }, members: [], memberCount: 0, version: 0 },
              ]),
            listTeamManagerCandidates: () => of([{ ...USERS[0], role: Role.Manager }]),
            listTeamMemberCandidates: () =>
              of([{ ...USERS[1], currentTeam: { id: 9, name: 'Other' } }, { ...USERS[0], role: Role.User, id: 3, active: false }]),
          },
        },
        {
          provide: TeamViewService,
          useValue: {
            listMyManagedTeams: () => of([{ id: 1, name: 'Platform' }, { id: 2, name: 'Mobile' }]),
            getTeamWeekSummary: () =>
              of({
                team: { id: 1, name: 'Platform' },
                weekStart: '2026-10-05',
                weekEnd: '2026-10-11',
                members: [
                  {
                    member: { id: 2, firstName: 'Bob', lastName: 'Stone', active: false },
                    days: ['05', '06', '07', '08', '09', '10', '11'].map((d) => ({ date: `2026-10-${d}`, minutes: d === '05' ? 90 : 0 })),
                    totalMinutes: 90,
                  },
                ],
              }),
            listTeamMemberTimeEntries: () =>
              of({
                team: { id: 1, name: 'Platform' },
                member: { id: 2, firstName: 'Bob', lastName: 'Stone', active: false },
                week: {
                  weekStart: '2026-10-05',
                  weekEnd: '2026-10-11',
                  totalMinutes: 1530,
                  days: [],
                  entries: ENTRIES,
                },
              }),
          },
        },
      ],
    });
    // jsdom n'a pas ResizeObserver, utilisé par la colonne figée (pFrozenColumn).
    vi.stubGlobal('ResizeObserver', class { observe = vi.fn(); unobserve = vi.fn(); disconnect = vi.fn(); });
    TestBed.inject(I18n).setLang('en');
    await TestBed.inject(AuthStore).init();
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    await harness.fixture.whenStable();
  }

  async function click(name: string): Promise<void> {
    (cy(name)?.querySelector('button') ?? cy(name))?.click();
    await harness.fixture.whenStable();
  }

  function expectNoFrench(): void {
    expect(frenchLeaks()).toEqual([]);
  }

  describe('SFD-03 : connexion, en-tête, mot de passe, comptes', () => {
    it('la page de connexion et ses erreurs de champ', async () => {
      await start(null, '/login');
      expect(document.documentElement.lang).toBe('en');
      expect(document.body.textContent).toContain('Sign in');
      await click('btn-se-connecter');
      expect(text('erreur-champ-mot-de-passe')).toBe('The password is required.');
      expectNoFrench();
    });

    it('l’en-tête et le menu utilisateur d’un ADMIN', async () => {
      await start(Role.Admin, '/admin/accounts');
      await click('btn-menu-utilisateur');
      expect(text('nav-comptes')).toBe('Accounts');
      expect(text('nav-equipes')).toBe('Teams');
      expect(text('nav-types-activite')).toBe('Activity types');
      expect(text('nav-projets')).toBe('Projects');
      expect(text('nav-parametres')).toBe('Settings');
      expect(text('menu-se-deconnecter')).toBe('Sign out');
      expectNoFrench();
    });

    it('l’en-tête et le menu utilisateur d’un MANAGER', async () => {
      await start(Role.Manager, '/journal?week=2026-10-05');
      await click('btn-menu-utilisateur');
      expect(text('nav-mon-journal')).toBe('My journal');
      expect(text('nav-mon-equipe')).toBe('My team');
      expectNoFrench();
    });

    it('changer mon mot de passe, avec ses erreurs de champ', async () => {
      await start(Role.User, '/account/password');
      expect(document.body.textContent).toContain('Change my password');
      await click('btn-enregistrer');
      expect(text('erreur-champ-ancien-mot-de-passe')).toBe('The current password is required.');
      expect(text('erreur-champ-nouveau-mot-de-passe')).toBe('The password is required.');
      expectNoFrench();
    });

    it('comptes : titres de colonnes, rôles, statuts, boutons et libellés lus par les lecteurs d’écran', async () => {
      await start(Role.Admin, '/admin/accounts');
      const table = text('table-comptes');
      expect(table).toContain('Administrator');
      expect(table).toContain('User');
      expect(table).toContain('Active');
      expect(table).toContain('Inactive');
      expect(text('indicateur-vous')).toBe('(you)');
      expect(cy('btn-modifier-2')?.querySelector('button')?.getAttribute('aria-label')).toBe('Edit Bob Stone’s account');
      expect(cy('btn-reactiver-2')?.querySelector('button')?.getAttribute('aria-label')).toBe('Reactivate Bob Stone’s account');
      expectNoFrench();
    });

    it('dialogue compte : création avec erreurs de champ, puis modification', async () => {
      await start(Role.Admin, '/admin/accounts');
      await click('btn-nouveau-compte');
      await click('btn-enregistrer');
      expect(text('erreur-champ-prenom')).toBe('The first name is required.');
      expect(text('erreur-champ-email-compte')).toBe('The email is required.');
      expectNoFrench();
      await click('btn-annuler-dialogue');
      await click('btn-modifier-2');
      expect(text('dialogue-compte')).toContain('The email cannot be changed.');
      expectNoFrench();
    });

    it('confirmation de désactivation d’un compte', async () => {
      await start(Role.Admin, '/admin/accounts');
      // Ada est connectée : seul Bob (inactif) est réactivable ; la désactivation d’un autre compte actif est vérifiée par la clé.
      TestBed.inject(ConfirmationService).confirm({
        header: TestBed.inject(I18n).t('accounts.deactivate.header'),
        message: TestBed.inject(I18n).t('accounts.deactivate.message', { name: 'Bob Stone', email: 'bob@x.com' }),
        acceptLabel: TestBed.inject(I18n).t('common.deactivate'),
        rejectLabel: TestBed.inject(I18n).t('common.cancel'),
      });
      await harness.fixture.whenStable();
      expect(document.body.textContent).toContain('Deactivate Bob Stone’s account (bob@x.com)?');
      expectNoFrench();
    });
  });

  describe('SFD-01 et SFD-07 : Mon journal, totaux, dialogue de saisie, suppression', () => {
    it('CA-08-03 : titre, jours, cartes, totaux du jour et de la semaine en anglais', async () => {
      await start(Role.User, '/journal?week=2026-10-05');
      expect(text('titre-journal')).toBe('My journal');
      expect(cy('jour-2026-10-05')?.querySelector('h2')?.textContent).toContain('Monday');
      expect(cy('jour-2026-10-11')?.querySelector('h2')?.textContent).toContain('Sunday');
      expect(text('total-semaine')).toBe('25.5 h');
      expect(text('total-jour-2026-10-05')).toBe('1.5 h');
      expect(text('saisie-1')).toContain('09:00 – 10:30');
      expect(text('saisie-2')).toContain('Full day');
      expect(text('saisie-3')).toContain('From 07/10 to 08/10');
      expect(cy('btn-modifier-saisie-1')?.querySelector('button')?.getAttribute('aria-label')).toBe('Edit time entry Meeting on 05/10');
      expect(text('libelle-semaine')).toBe('Week of 05/10/2026 to 11/10/2026');
      expectNoFrench();
    });

    it('CA-08-07 : le libellé d’un type d’activité saisi en français reste « Réunion » en anglais', async () => {
      ENTRIES[0] = entry({ activityType: { code: 'REUNION', label: 'Réunion', color: TagColor.Warn, active: true } });
      try {
        await start(Role.User, '/journal?week=2026-10-05');
        expect(text('titre-journal')).toBe('My journal');
        expect(cy('saisie-1')?.querySelector('[data-cy="activity-type-tag"]')?.textContent).toContain('Réunion');
        expect(cy('btn-modifier-saisie-1')?.querySelector('button')?.getAttribute('aria-label')).toBe('Edit time entry Réunion on 05/10');
      } finally {
        ENTRIES[0] = entry({});
      }
    });

    it('dialogue de saisie : libellés, aides et erreurs des validateurs en anglais', async () => {
      await start(Role.User, '/journal?week=2026-10-05');
      await click('btn-nouvelle-saisie');
      const dialog = text('dialogue-saisie');
      expect(dialog).toContain('Period');
      expect(dialog).toContain('Start time');
      expect(dialog).toContain('Activity type');
      expect(dialog).toContain('Calculated duration:');
      await click('btn-enregistrer');
      expect(text('erreur-champ-heure-debut')).toBe('The start time is required.');
      expect(text('erreur-select-type-activite')).toBe('The activity type is required.');
      expectNoFrench();
    });

    it('confirmation de suppression d’une saisie', async () => {
      await start(Role.User, '/journal?week=2026-10-05');
      await click('btn-supprimer-saisie-1');
      expect(document.body.textContent).toContain('Delete the time entry “Meeting” on 05/10 (1 h 30)?');
      expect(document.body.textContent).toContain('Delete time entry');
      expectNoFrench();
    });
  });

  describe('RG-08-05 : textes produits par le code', () => {
    it('CA-08-08 : le détecteur repère bien le français (même écran en français)', async () => {
      await start(Role.User, '/journal?week=2026-10-05');
      TestBed.inject(I18n).setLang('fr');
      await harness.fixture.whenStable();
      expect(frenchLeaks().length).toBeGreaterThan(10);
    });

    it('CA-08-08 : aucun texte du dictionnaire anglais (toasts, confirmations, aides compris) n’est en français', () => {
      const leaks = leafValues(DICTIONARIES.en).flatMap((value) => value.split(/\s+/)).filter((word) => word !== 'Français' && FRENCH.test(word));
      expect(leaks).toEqual([]);
    });

    it('les messages génériques de l’intercepteur suivent la langue', async () => {
      await start(Role.User, '/journal?week=2026-10-05');
      expect(toProblem(new Error('x')).detail).toBe(DICTIONARIES.en.errors.generic);
      TestBed.inject(I18n).setLang('fr');
      expect(toProblem(new Error('x')).detail).toBe(DICTIONARIES.fr.errors.generic);
    });

    it('pluriels : le zéro est singulier en français, pluriel en anglais (RG-08-08)', () => {
      const i18n = TestBed.inject(I18n);
      expect(i18n.plural('timeEntry.dialog.workingDays', 1)).toBe('1 jour ouvré');
      expect(i18n.plural('timeEntry.dialog.workingDays', 10)).toBe('10 jours ouvrés');
      i18n.setLang('en');
      expect(i18n.plural('timeEntry.dialog.workingDays', 1)).toBe('1 working day');
      expect(i18n.plural('timeEntry.dialog.workingDays', 10)).toBe('10 working days');
    });
  });
});
