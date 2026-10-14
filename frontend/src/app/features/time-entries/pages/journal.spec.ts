import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import {
  ActivityTypesService,
  AppSettingsService,
  Granularity,
  ProjectsService,
  TagColor,
  TimeEntriesService,
  TimeEntryResponse,
  WeeklyTimeEntriesResponse,
} from '../../../core/api/generated';
import { mondayOf, todayInParis } from '../../../shared/dates/week';
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
    durationMinutes: 420,
    ...over,
  });
const week = (
  entries: TimeEntryResponse[],
  totalMinutes: number,
  weekStart = '2026-10-05',
): WeeklyTimeEntriesResponse => ({ weekStart, weekEnd: '2026-10-11', totalMinutes, entries });

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
          useValue: { getAppSettings: () => of({ standardDayMinutes: 420, version: 0 }) },
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

  it('affiche l’état vide avec « Nouvelle saisie » quand la semaine n’a aucune saisie', async () => {
    listMyTimeEntries.mockReturnValue(of(week([], 0)));
    await open();
    expect(cy('etat-vide')?.textContent).toContain('Aucune activité cette semaine.');
    expect(cy('btn-nouvelle-saisie-vide')).not.toBeNull();
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

  it('CA-01-01 : la saisie du 05/10/2026 de 09:00 à 10:30 en REUNION apparaît dans le journal avec 1 h 30', async () => {
    listMyTimeEntries.mockReturnValue(of(week([meeting()], 90)));
    await open();
    expect(listMyTimeEntries.mock.calls[0][0]).toBe('2026-10-05');
    const card = cy('jour-2026-10-05')?.querySelector('[data-cy="saisie-1"]');
    expect(card?.textContent).toContain('Réunion');
    expect(card?.textContent).toContain('09:00 – 10:30');
    expect(card?.textContent).toContain('1 h 30');
  });

  it('CA-01-09 : avec des saisies de 90 min et 7 h dans la semaine, le total affiché est 8,5 h', async () => {
    listMyTimeEntries.mockReturnValue(of(week([meeting(), fullDay()], 510)));
    await open();
    expect(cy('total-semaine')?.textContent).toContain('8,5 h');
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
              durationMinutes: 4200,
            }),
          ],
          2100,
        ),
      ),
    );
    await open();
    (cy('jour-2026-10-05')?.querySelector('[data-cy="btn-supprimer-saisie-1"] button') as HTMLElement).click();
    expect(confirm.mock.calls[0][0].message).toBe(
      'Supprimer la saisie « Formation » du 05/10 au 16/10 (70 h 00) ?',
    );
  });

  it('RG-01-11 : la suppression confirmée retire la saisie du journal, met à jour le total et affiche un toast de 3 s', async () => {
    listMyTimeEntries.mockReturnValueOnce(of(week([meeting(), fullDay()], 510)));
    deleteTimeEntry.mockReturnValue(of(null));
    await open();
    expect(cy('total-semaine')?.textContent).toContain('8,5 h');
    await click('btn-supprimer-saisie-1');
    listMyTimeEntries.mockReturnValue(of(week([fullDay()], 420)));
    confirm.mock.calls[0][0].accept();
    await harness.fixture.whenStable();
    expect(deleteTimeEntry).toHaveBeenCalledWith(1);
    expect(cy('saisie-1')).toBeNull();
    expect(cy('saisie-2')).not.toBeNull();
    expect(cy('total-semaine')?.textContent).toContain('7 h');
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
});
