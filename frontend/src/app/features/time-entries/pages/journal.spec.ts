import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import {
  ActivityTypesService,
  CreateTimeEntryRequest,
  UpdateTimeEntryRequest,
  AppSettingsService,
  Granularity,
  ProjectsService,
  TagColor,
  TimeEntriesService,
  TimeEntryResponse,
  WeeklyTimeEntriesResponse,
} from '../../../core/api/generated';
import { mondayOf, todayInParis } from '../../../shared/dates/week';
import { TimeEntriesStore } from '../data/time-entries.store';
import { JournalPage } from './journal';

const meeting = (over: Partial<TimeEntryResponse> = {}): TimeEntryResponse => ({
  id: 1,
  granularity: Granularity.Hour,
  startDate: '2026-10-05',
  startTime: '09:00',
  endTime: '10:30',
  activityType: { code: 'REUNION', label: 'Réunion', color: TagColor.Warn, active: true },
  durationMinutes: 90,
  version: 0,
  ...over,
});
const fullDay = (over: Partial<TimeEntryResponse> = {}): TimeEntryResponse =>
  meeting({
    id: 2,
    granularity: Granularity.Day,
    startDate: '2026-10-06',
    startTime: null,
    endTime: null,
    activityType: { code: 'TRAVAIL_PROJET', label: 'Travail projet', color: TagColor.Info, active: true },
    project: { code: 'APOLLO', name: 'Apollo', active: true },
    durationMinutes: 480,
    ...over,
  });
const week = (
  entries: TimeEntryResponse[],
  totalMinutes: number,
  weekStart = '2026-10-05',
): WeeklyTimeEntriesResponse => ({ weekStart, weekEnd: '2026-10-11', totalMinutes, days: [], entries });

/** Semaine du 05/10/2026 avec les totaux par jour de l'API (lundi → dimanche), à 0 par défaut. */
const weekWithDays = (
  entries: TimeEntryResponse[],
  minutesByDate: Record<string, number>,
): WeeklyTimeEntriesResponse => {
  const days = ['05', '06', '07', '08', '09', '10', '11'].map((day) => ({
    date: `2026-10-${day}`,
    minutes: minutesByDate[`2026-10-${day}`] ?? 0,
  }));
  return {
    weekStart: '2026-10-05',
    weekEnd: '2026-10-11',
    totalMinutes: days.reduce((sum, day) => sum + day.minutes, 0),
    days,
    entries,
  };
};

describe('JournalPage', () => {
  const listMyTimeEntries = vi.fn();
  const deleteTimeEntry = vi.fn();
  const confirm = vi.fn();
  const add = vi.fn();
  let harness: RouterTestingHarness;
  let root: HTMLElement;

  async function open(url = '/journal?semaine=2026-10-05'): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'journal', component: JournalPage }]),
        { provide: TimeEntriesService, useValue: { listMyTimeEntries, deleteTimeEntry } },
        { provide: ActivityTypesService, useValue: { listActivityTypes: () => of([]) } },
        { provide: ProjectsService, useValue: { listProjects: () => of([]) } },
        {
          provide: AppSettingsService,
          useValue: { getAppSettings: () => of({ standardDayMinutes: 480, version: 0 }) },
        },
        { provide: ConfirmationService, useValue: { confirm } },
        { provide: MessageService, useValue: { add } },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, JournalPage);
    root = harness.routeNativeElement as HTMLElement;
    await harness.fixture.whenStable();
  }

  const cy = (name: string) => root.querySelector<HTMLElement>(`[data-cy="${name}"]`);
  const click = async (name: string) => {
    (cy(name)?.querySelector('button') ?? cy(name))?.click();
    await harness.fixture.whenStable();
  };

  beforeEach(() => {
    for (const fn of [listMyTimeEntries, deleteTimeEntry, confirm, add]) {
      fn.mockReset();
    }
    TestBed.resetTestingModule();
  });

  it('affiche l’état de chargement tant que la semaine n’est pas arrivée', async () => {
    listMyTimeEntries.mockReturnValue(new Subject());
    await open();
    expect(cy('etat-chargement')).not.toBeNull();
    expect(cy('journal-semaine')).toBeNull();
    expect(cy('titre-journal')?.textContent).toContain('Mon journal');
  });

  it('affiche l’état vide sans répéter « Nouvelle saisie » quand la semaine n’a aucune saisie', async () => {
    listMyTimeEntries.mockReturnValue(of(week([], 0)));
    await open();
    expect(cy('etat-vide')?.textContent).toContain('Aucune activité cette semaine.');
    // Un seul « Nouvelle saisie », en haut à droite : l'état vide n'en répète pas.
    expect(cy('btn-nouvelle-saisie-vide')).toBeNull();
    expect(root.querySelectorAll('[data-cy^="btn-nouvelle-saisie"]')).toHaveLength(1);
    expect(cy('btn-nouvelle-saisie')).not.toBeNull();
  });

  it('affiche l’état d’erreur avec « Réessayer », qui recharge la semaine', async () => {
    listMyTimeEntries.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })),
    );
    await open();
    expect(cy('etat-erreur')?.textContent).toContain('Panne serveur');
    listMyTimeEntries.mockReturnValue(of(week([meeting()], 90)));
    await click('btn-reessayer');
    expect(cy('etat-erreur')).toBeNull();
    expect(cy('journal-semaine')).not.toBeNull();
  });

  it('CA-01-01 : la saisie du 05/10/2026 de 09:00 à 10:30 en REUNION apparaît dans le journal, total 1,5 h', async () => {
    listMyTimeEntries.mockReturnValue(of(week([meeting()], 90)));
    await open();
    expect(listMyTimeEntries.mock.calls[0][0]).toBe('2026-10-05');
    const card = cy('jour-2026-10-05')?.querySelector('[data-cy="saisie-1"]');
    expect(card?.textContent).toContain('Réunion');
    expect(card?.textContent).toContain('09:00 – 10:30');
    expect(cy('total-semaine')?.textContent).toContain('1,5 h');
  });

  it('SFD-01 E1 : la carte affiche la description de la saisie à la place de sa durée', async () => {
    const long = 'Point hebdo. '.repeat(40);
    listMyTimeEntries.mockReturnValue(
      of(week([meeting({ comment: 'Point hebdo avec l’équipe' }), fullDay({ comment: long })], 570)),
    );
    await open();
    const card = cy('saisie-1') as HTMLElement;
    expect(card.querySelector('[data-cy="saisie-duree"]')).toBeNull();
    // Texte exact : aucune espace ni ligne vide ajoutée autour du commentaire.
    expect(card.querySelector('[data-cy="saisie-commentaire"]')?.textContent).toBe(
      'Point hebdo avec l’équipe',
    );
    // Description longue limitée à 3 lignes, texte complet au survol et dans le DOM.
    const longComment = cy('saisie-2')?.querySelector('[data-cy="saisie-commentaire"]');
    expect(longComment?.classList).toContain('line-clamp-3');
    expect(longComment?.getAttribute('title')).toBe(long);
  });

  it('SFD-01 E1 : une saisie sans description n’affiche ni description ni durée', async () => {
    listMyTimeEntries.mockReturnValue(of(week([meeting()], 90)));
    await open();
    const card = cy('saisie-1') as HTMLElement;
    expect(card.querySelector('[data-cy="saisie-commentaire"]')).toBeNull();
    expect(card.querySelector('[data-cy="saisie-duree"]')).toBeNull();
  });

  it('CA-01-09 : avec des saisies de 90 min et 8 h dans la semaine, le total affiché est 9,5 h', async () => {
    listMyTimeEntries.mockReturnValue(of(week([meeting(), fullDay()], 570)));
    await open();
    expect(cy('total-semaine')?.textContent).toContain('9,5 h');
  });

  it('CA-02-07 : une saisie dont le type a été désactivé reste affichée avec son libellé et sa couleur', async () => {
    listMyTimeEntries.mockReturnValue(
      of(
        week(
          [
            meeting({
              activityType: { code: 'REUNION', label: 'Réunion', color: TagColor.Warn, active: false },
            }),
          ],
          90,
        ),
      ),
    );
    await open();
    expect(cy('saisie-1')?.textContent).toContain('Réunion');
    expect(cy('saisie-1')?.querySelector('.p-tag-warn')).not.toBeNull();
  });

  it('SFD-01 E1 : « Nouvelle saisie » ouvre le dialogue de saisie', async () => {
    listMyTimeEntries.mockReturnValue(of(week([], 0)));
    await open();
    const page = harness.routeDebugElement?.componentInstance as { dialogVisible(): boolean };
    expect(page.dialogVisible()).toBe(false);
    await click('btn-nouvelle-saisie');
    expect(page.dialogVisible()).toBe(true);
  });

  it('CA-01-01 : après l’enregistrement, un toast de 3 s nomme la saisie et le journal se place sur sa semaine', async () => {
    listMyTimeEntries.mockReturnValue(of(week([], 0)));
    await open('/journal?semaine=2026-10-12');
    const page = harness.routeDebugElement?.componentInstance as {
      onSaved(event: { mode: 'create' | 'update'; entry: TimeEntryResponse }): void;
    };
    page.onSaved({ mode: 'create', entry: meeting() });
    await harness.fixture.whenStable();
    expect(add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'success',
        summary: 'Saisie enregistrée',
        detail: 'Réunion du 05/10 (1 h 30).',
        life: 3000,
      }),
    );
    expect(TestBed.inject(Router).url).toBe('/journal?semaine=2026-10-05');
    expect(listMyTimeEntries.mock.calls.at(-1)?.[0]).toBe('2026-10-05');
  });

  it('SFD-01 E1 : le « + » d’un jour ouvre une création à la date de ce jour, « Nouvelle saisie » sans date proposée', async () => {
    listMyTimeEntries.mockReturnValue(of(week([meeting()], 90)));
    await open();
    const page = harness.routeDebugElement?.componentInstance as {
      dialogVisible: { (): boolean; set(value: boolean): void };
      editing(): TimeEntryResponse | null;
      initialDate(): string | null;
    };
    await click('btn-ajouter-saisie-2026-10-07');
    expect(page.dialogVisible()).toBe(true);
    expect(page.editing()).toBeNull();
    expect(page.initialDate()).toBe('2026-10-07');
    page.dialogVisible.set(false);
    await click('btn-nouvelle-saisie');
    expect(page.initialDate()).toBeNull();
  });

  it('SFD-01 E1 : une semaine sans saisie affiche quand même ses jours et leurs « + »', async () => {
    listMyTimeEntries.mockReturnValue(of(week([], 0)));
    await open();
    expect(cy('etat-vide')).not.toBeNull();
    expect(cy('jour-2026-10-05')).not.toBeNull();
    expect(cy('btn-ajouter-saisie-2026-10-05')).not.toBeNull();
  });

  it('RG-01-12 : « Modifier » ouvre le dialogue sur la saisie, « Nouvelle saisie » repart d’une création', async () => {
    listMyTimeEntries.mockReturnValue(of(week([meeting()], 90)));
    await open();
    const page = harness.routeDebugElement?.componentInstance as {
      dialogVisible: { (): boolean; set(value: boolean): void };
      editing(): TimeEntryResponse | null;
    };
    await click('btn-modifier-saisie-1');
    expect(page.dialogVisible()).toBe(true);
    expect(page.editing()).toEqual(expect.objectContaining({ id: 1 }));
    page.dialogVisible.set(false);
    await click('btn-nouvelle-saisie');
    expect(page.editing()).toBeNull();
  });

  it('le toast d’une modification a le titre « Saisie modifiée »', async () => {
    listMyTimeEntries.mockReturnValue(of(week([meeting()], 90)));
    await open();
    const page = harness.routeDebugElement?.componentInstance as {
      onSaved(event: { mode: 'create' | 'update'; entry: TimeEntryResponse }): void;
    };
    page.onSaved({ mode: 'update', entry: meeting() });
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Saisie modifiée' }));
  });

  it('SFD-01 E3 : « Supprimer » demande une confirmation explicite ; annulée, elle n’appelle pas l’API', async () => {
    listMyTimeEntries.mockReturnValue(of(week([meeting()], 90)));
    await open();
    await click('btn-supprimer-saisie-1');
    expect(confirm).toHaveBeenCalledTimes(1);
    const options = confirm.mock.calls[0][0];
    expect(options.message).toBe('Supprimer la saisie « Réunion » du 05/10 (1 h 30) ?');
    expect(options.acceptLabel).toBe('Supprimer');
    // Échap doit annuler : la confirmation ne doit jamais désactiver `closable`.
    expect(options.closable).not.toBe(false);
    expect(deleteTimeEntry).not.toHaveBeenCalled();
    expect(cy('saisie-1')).not.toBeNull();
  });

  it('SFD-01 E3 : la confirmation d’une PLAGE cite ses deux bornes', async () => {
    listMyTimeEntries.mockReturnValue(
      of(
        week(
          [
            meeting({
              granularity: Granularity.Range,
              endDate: '2026-10-16',
              startTime: null,
              endTime: null,
              activityType: { code: 'FORMATION', label: 'Formation', color: TagColor.Success, active: true },
              durationMinutes: 4800,
            }),
          ],
          2100,
        ),
      ),
    );
    await open();
    (cy('jour-2026-10-05')?.querySelector('[data-cy="btn-supprimer-saisie-1"] button') as HTMLElement).click();
    expect(confirm.mock.calls[0][0].message).toBe(
      'Supprimer la saisie « Formation » du 05/10 au 16/10 (80 h 00) ?',
    );
  });

  it('RG-01-11 : la suppression confirmée retire la saisie du journal, met à jour le total et affiche un toast de 3 s', async () => {
    listMyTimeEntries.mockReturnValueOnce(of(week([meeting(), fullDay()], 570)));
    deleteTimeEntry.mockReturnValue(of(null));
    await open();
    expect(cy('total-semaine')?.textContent).toContain('9,5 h');
    await click('btn-supprimer-saisie-1');
    listMyTimeEntries.mockReturnValue(of(week([fullDay()], 480)));
    confirm.mock.calls[0][0].accept();
    await harness.fixture.whenStable();
    expect(deleteTimeEntry).toHaveBeenCalledWith(1);
    expect(cy('saisie-1')).toBeNull();
    expect(cy('saisie-2')).not.toBeNull();
    expect(cy('total-semaine')?.textContent).toContain('8 h');
    expect(add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'success', summary: 'Saisie supprimée', life: 3000 }),
    );
  });

  it('une suppression confirmée deux fois n’envoie qu’une requête', async () => {
    listMyTimeEntries.mockReturnValue(of(week([meeting()], 90)));
    deleteTimeEntry.mockReturnValue(new Subject());
    await open();
    await click('btn-supprimer-saisie-1');
    const { accept } = confirm.mock.calls[0][0];
    accept();
    accept();
    expect(deleteTimeEntry).toHaveBeenCalledTimes(1);
    expect(add).not.toHaveBeenCalled();
  });

  it('RG-01-13 : sans semaine dans l’URL, affiche la semaine courante (lundi à Paris)', async () => {
    listMyTimeEntries.mockReturnValue(new Subject());
    await open('/journal');
    expect(listMyTimeEntries.mock.calls[0][0]).toBe(mondayOf(todayInParis()));
  });

  it('une date de l’URL qui n’est pas un lundi est ramenée au lundi de sa semaine, une valeur illisible à la semaine courante', async () => {
    listMyTimeEntries.mockReturnValue(new Subject());
    await open('/journal?semaine=2026-10-08');
    expect(listMyTimeEntries.mock.calls[0][0]).toBe('2026-10-05');
    expect(cy('libelle-semaine')?.textContent).toContain('Semaine du 05/10/2026 au 11/10/2026');
    await harness.navigateByUrl('/journal?semaine=demain');
    await harness.fixture.whenStable();
    expect(listMyTimeEntries.mock.calls.at(-1)?.[0]).toBe(mondayOf(todayInParis()));
  });

  it('SFD-01 E1 : « Semaine suivante » et « Semaine précédente » changent la semaine de l’URL et la rechargent', async () => {
    listMyTimeEntries.mockReturnValue(of(week([meeting()], 90)));
    await open();
    listMyTimeEntries.mockReturnValue(of(week([], 0, '2026-10-12')));
    await click('btn-semaine-suivante');
    expect(TestBed.inject(Router).url).toBe('/journal?semaine=2026-10-12');
    expect(listMyTimeEntries.mock.calls.at(-1)?.[0]).toBe('2026-10-12');
    expect(cy('libelle-semaine')?.textContent).toContain('Semaine du 12/10/2026 au 18/10/2026');
    await click('btn-semaine-precedente');
    await click('btn-semaine-precedente');
    expect(TestBed.inject(Router).url).toBe('/journal?semaine=2026-09-28');
    expect(listMyTimeEntries.mock.calls.at(-1)?.[0]).toBe('2026-09-28');
  });

  describe('SFD-07 : total du jour', () => {
    const total = (iso: string) => cy(`total-jour-${iso}`)?.textContent?.trim();
    const label = (iso: string) =>
      cy(`jour-${iso}`)?.querySelector('.sr-only')?.textContent?.trim();

    it('CA-07-01 : le lundi 05/10/2026 avec 09:00–10:30 et 14:00–16:00 affiche 3,5 h', async () => {
      listMyTimeEntries.mockReturnValue(
        of(
          weekWithDays(
            [meeting(), meeting({ id: 3, startTime: '14:00', endTime: '16:00', durationMinutes: 120 })],
            { '2026-10-05': 210 },
          ),
        ),
      );
      await open();
      expect(total('2026-10-05')).toBe('3,5 h');
      expect(label('2026-10-05')).toBe('Total du lundi : 3,5 h');
    });

    it('CA-07-02 : une PLAGE du jeudi au mardi affiche 8 h le jeudi et le vendredi, « – » le week-end', async () => {
      const range = meeting({
        granularity: Granularity.Range,
        startDate: '2026-10-08',
        endDate: '2026-10-13',
        startTime: null,
        endTime: null,
        durationMinutes: 2880,
      });
      listMyTimeEntries.mockReturnValue(of(weekWithDays([range], { '2026-10-08': 480, '2026-10-09': 480 })));
      await open();
      expect(total('2026-10-08')).toBe('8 h');
      expect(total('2026-10-09')).toBe('8 h');
      expect(total('2026-10-10')).toBe('–');
      expect(total('2026-10-11')).toBe('–');
    });

    it('CA-07-03 : une JOURNEE le samedi 10/10/2026 affiche 8 h', async () => {
      listMyTimeEntries.mockReturnValue(
        of(weekWithDays([fullDay({ startDate: '2026-10-10', durationMinutes: 480 })], { '2026-10-10': 480 })),
      );
      await open();
      expect(total('2026-10-10')).toBe('8 h');
    });

    it('CA-07-04 : après la suppression de la saisie, le lundi passe de 3,5 h à 2 h et le total de la semaine diminue de 1,5 h', async () => {
      const second = meeting({ id: 3, startTime: '14:00', endTime: '16:00', durationMinutes: 120 });
      listMyTimeEntries.mockReturnValueOnce(of(weekWithDays([meeting(), second], { '2026-10-05': 210 })));
      deleteTimeEntry.mockReturnValue(of(null));
      await open();
      expect(total('2026-10-05')).toBe('3,5 h');
      expect(cy('total-semaine')?.textContent).toContain('3,5 h');
      await click('btn-supprimer-saisie-1');
      listMyTimeEntries.mockReturnValue(of(weekWithDays([second], { '2026-10-05': 120 })));
      confirm.mock.calls[0][0].accept();
      await harness.fixture.whenStable();
      expect(total('2026-10-05')).toBe('2 h');
      expect(cy('total-semaine')?.textContent).toContain('2 h');
    });

    it('RG-07-07 : après une création puis une modification, la semaine est rechargée et le total du jour suit', async () => {
      const createTimeEntry = vi.fn().mockReturnValue(of(meeting()));
      const updateTimeEntry = vi.fn().mockReturnValue(of(meeting()));
      listMyTimeEntries.mockReturnValueOnce(of(weekWithDays([], {})));
      await open();
      Object.assign(TestBed.inject(TimeEntriesService), { createTimeEntry, updateTimeEntry });
      const store = TestBed.inject(TimeEntriesStore);
      expect(total('2026-10-05')).toBe('–');

      listMyTimeEntries.mockReturnValue(of(weekWithDays([meeting()], { '2026-10-05': 90 })));
      store.create({} as CreateTimeEntryRequest).subscribe();
      await harness.fixture.whenStable();
      expect(total('2026-10-05')).toBe('1,5 h');

      listMyTimeEntries.mockReturnValue(of(weekWithDays([meeting()], { '2026-10-05': 120 })));
      store.update(1, {} as UpdateTimeEntryRequest).subscribe();
      await harness.fixture.whenStable();
      expect(total('2026-10-05')).toBe('2 h');
    });

    it('CA-07-05 : 90 min le lundi et une JOURNEE le mardi donnent 1,5 h, 8 h, « – » ailleurs, et 9,5 h pour la semaine', async () => {
      listMyTimeEntries.mockReturnValue(
        of(weekWithDays([meeting(), fullDay({ durationMinutes: 480 })], { '2026-10-05': 90, '2026-10-06': 480 })),
      );
      await open();
      expect(total('2026-10-05')).toBe('1,5 h');
      expect(total('2026-10-06')).toBe('8 h');
      for (const day of ['07', '08', '09', '10', '11']) {
        expect(total(`2026-10-${day}`)).toBe('–');
      }
      expect(cy('total-semaine')?.textContent).toContain('9,5 h');
    });

    it('CA-07-07 : un MANAGER voit les totaux de ses jours sur la même page « Mon journal »', async () => {
      listMyTimeEntries.mockReturnValue(of(weekWithDays([meeting()], { '2026-10-05': 90 })));
      await open();
      expect(cy('titre-journal')?.textContent).toContain('Mon journal');
      expect(total('2026-10-05')).toBe('1,5 h');
      expect(root.querySelectorAll('[data-cy^="total-jour-"]')).toHaveLength(7);
    });

    it('CA-07-08 : une saisie de 09:00 à 09:15 le lundi affiche 0,25 h', async () => {
      listMyTimeEntries.mockReturnValue(
        of(weekWithDays([meeting({ endTime: '09:15', durationMinutes: 15 })], { '2026-10-05': 15 })),
      );
      await open();
      expect(total('2026-10-05')).toBe('0,25 h');
    });

    it('RG-07-01 : une semaine sans saisie affiche « – » chaque jour, lu « Total du lundi : 0 h »', async () => {
      listMyTimeEntries.mockReturnValue(of(weekWithDays([], {})));
      await open();
      expect(total('2026-10-05')).toBe('–');
      expect(label('2026-10-05')).toBe('Total du lundi : 0 h');
      expect(cy('total-jour-2026-10-05')?.getAttribute('aria-hidden')).toBe('true');
    });
  });
});
