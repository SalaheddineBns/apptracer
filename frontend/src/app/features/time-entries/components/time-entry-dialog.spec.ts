import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import {
  ActivityTypeResponse,
  ActivityTypesService,
  AppSettingsService,
  Granularity,
  ProjectResponse,
  ProjectsService,
  TagColor,
  TimeEntriesService,
  TimeEntryResponse,
} from '../../../core/api/generated';
import { addDays, isoToLocalDate, todayInParis } from '../../../shared/dates/week';
import { TimeEntryDialog } from './time-entry-dialog';
import { quarterHourFloor, timeStringToDate } from './time-entry-period';

const date = (iso: string) => isoToLocalDate(iso);
const time = (value: string) => timeStringToDate(value);
const toTime = (date: Date) =>
  `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
const type = (id: number, code: string, projectRequired = false): ActivityTypeResponse => ({
  id,
  code,
  label: code,
  projectRequired,
  color: TagColor.Info,
  active: true,
  version: 0,
});
const project = (id: number, code: string): ProjectResponse => ({
  id,
  code,
  name: code,
  active: true,
  version: 0,
});
const savedEntry = (over: Partial<TimeEntryResponse> = {}): TimeEntryResponse => ({
  id: 7,
  granularity: Granularity.Hour,
  startDate: '2026-10-05',
  startTime: '09:00',
  endTime: '10:30',
  activityType: { code: 'REUNION', label: 'Réunion', color: TagColor.Warn, active: true },
  durationMinutes: 90,
  version: 0,
  ...over,
});
const httpError = (status: number, body: object) =>
  throwError(() => new HttpErrorResponse({ status, error: body }));

describe('TimeEntryDialog', () => {
  const listActivityTypes = vi.fn();
  const listProjects = vi.fn();
  const getAppSettings = vi.fn();
  const createTimeEntry = vi.fn();
  const updateTimeEntry = vi.fn();
  const listMyTimeEntries = vi.fn();
  const add = vi.fn();
  const saved = vi.fn();
  const conflict = vi.fn();
  let fixture: ReturnType<typeof TestBed.createComponent<TimeEntryDialog>>;

  async function open(
    entry: TimeEntryResponse | null = null,
    initialDate: string | null = null,
  ): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        { provide: ActivityTypesService, useValue: { listActivityTypes } },
        { provide: ProjectsService, useValue: { listProjects } },
        { provide: AppSettingsService, useValue: { getAppSettings } },
        {
          provide: TimeEntriesService,
          useValue: { createTimeEntry, updateTimeEntry, listMyTimeEntries },
        },
        { provide: MessageService, useValue: { add } },
      ],
    });
    fixture = TestBed.createComponent(TimeEntryDialog);
    fixture.componentInstance.saved.subscribe(saved);
    fixture.componentInstance.conflict.subscribe(conflict);
    fixture.componentRef.setInput('entry', entry);
    fixture.componentRef.setInput('initialDate', initialDate);
    fixture.componentRef.setInput('visible', true);
    await fixture.whenStable();
    await fixture.whenStable();
  }

  const form = () => (fixture.componentInstance as unknown as { form: FormGroup }).form;
  const el = (cy: string) => document.body.querySelector<HTMLElement>(`[data-cy="${cy}"]`);
  const error = (cy: string) => el(`erreur-${cy}`)?.textContent ?? null;
  const duration = () => el('duree-calculee')?.textContent?.trim();
  async function patch(value: Record<string, unknown>): Promise<void> {
    form().patchValue(value);
    form().markAllAsTouched();
    await fixture.whenStable();
  }
  async function fill(value: Record<string, unknown>): Promise<void> {
    form().patchValue(value);
    await fixture.whenStable();
  }
  async function submit(): Promise<void> {
    el('btn-enregistrer')?.querySelector('button')?.click();
    await fixture.whenStable();
  }
  const meeting = {
    startDate: date('2026-10-05'),
    startTime: time('09:00'),
    endTime: time('10:30'),
    activityTypeCode: 'REUNION',
  };

  beforeEach(() => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe = vi.fn();
        unobserve = vi.fn();
        disconnect = vi.fn();
      },
    );
    for (const fn of [
      listActivityTypes,
      listProjects,
      getAppSettings,
      createTimeEntry,
      updateTimeEntry,
      listMyTimeEntries,
      add,
      saved,
      conflict,
    ]) {
      fn.mockReset();
    }
    listActivityTypes.mockReturnValue(
      of([type(1, 'FORMATION'), type(2, 'REUNION'), type(3, 'TRAVAIL_PROJET', true)]),
    );
    listProjects.mockReturnValue(of([project(1, 'APOLLO'), project(2, 'GEMINI')]));
    getAppSettings.mockReturnValue(of({ standardDayMinutes: 420, version: 0 }));
    TestBed.resetTestingModule();
    document.body.querySelectorAll('p-dialog, .p-dialog-mask').forEach((n) => n.remove());
  });

  describe('période', () => {
    it('SFD-01 E2 : propose heure, journée et plage ; en HEURE, la date et les deux heures', async () => {
      await open();
      expect(el('select-granularite')?.textContent).toContain('Heure');
      expect(el('select-granularite')?.textContent).toContain('Journée');
      expect(el('select-granularite')?.textContent).toContain('Plage');
      expect(form().get('granularity')?.value).toBe(Granularity.Hour);
      expect(el('champ-date-debut')).not.toBeNull();
      expect(el('champ-heure-debut')).not.toBeNull();
      expect(el('champ-heure-fin')).not.toBeNull();
      expect(el('champ-date-fin')).toBeNull();
    });

    it('RG-01-02 : en JOURNEE, seule la date est demandée', async () => {
      await open();
      await patch({ granularity: Granularity.Day, startDate: date('2026-10-05') });
      expect(el('champ-heure-debut')).toBeNull();
      expect(el('champ-heure-fin')).toBeNull();
      expect(el('champ-date-fin')).toBeNull();
      expect(form().get('startTime')?.disabled).toBe(true);
      expect(form().get('endDate')?.disabled).toBe(true);
      expect(form().get('startDate')?.valid).toBe(true);
    });

    it('RG-01-03 : en PLAGE, la date de fin remplace les heures', async () => {
      await open();
      await patch({ granularity: Granularity.Range });
      expect(el('champ-date-fin')).not.toBeNull();
      expect(el('champ-heure-debut')).toBeNull();
      expect(el('dialogue-saisie')?.textContent).toContain('Date de début');
      expect(form().get('endTime')?.disabled).toBe(true);
    });

    it('CA-01-02 : une heure de fin antérieure à l’heure de début met le champ en erreur et bloque l’enregistrement', async () => {
      await open();
      await fill({ ...meeting, startTime: time('10:30'), endTime: time('09:00') });
      await submit();
      expect(error('champ-heure-fin')).toContain('après l’heure de début');
      expect(createTimeEntry).not.toHaveBeenCalled();
      expect(fixture.componentInstance.visible()).toBe(true);
    });

    it('RG-01-01 : une heure de fin égale à l’heure de début est refusée, puis acceptée dès que le début recule', async () => {
      await open();
      await patch({ startDate: date('2026-10-05'), startTime: time('09:00'), endTime: time('09:00') });
      expect(error('champ-heure-fin')).toContain('après l’heure de début');
      await patch({ startTime: time('08:45') });
      expect(error('champ-heure-fin')).toBeNull();
      expect(form().get('endTime')?.valid).toBe(true);
    });

    it('RG-01-01 : une heure hors du pas de 15 minutes est refusée telle que saisie', async () => {
      await open();
      await patch({ startDate: date('2026-10-05'), startTime: time('09:10'), endTime: time('10:00') });
      expect(error('champ-heure-debut')).toContain('pas de 15 minutes');
      expect((form().get('startTime')?.value as Date).getMinutes()).toBe(10);
      expect(duration()).toBe('—');
    });

    it('RG-01-01 : un créneau jusqu’à 23:45 est accepté, minuit ne l’est pas', async () => {
      await open();
      await patch({ startDate: date('2026-10-05'), startTime: time('22:00'), endTime: time('23:45') });
      expect(form().get('endTime')?.valid).toBe(true);
      await patch({ endTime: time('00:00') });
      expect(error('champ-heure-fin')).toContain('après l’heure de début');
    });

    it('CA-01-07 : une date de début dans le futur est refusée, l’enregistrement est bloqué', async () => {
      await open();
      await fill({ ...meeting, startDate: date(addDays(todayInParis(), 1)) });
      await submit();
      expect(error('champ-date-debut')).toContain('ne peut pas être dans le futur');
      expect(createTimeEntry).not.toHaveBeenCalled();
    });

    it('RG-01-07 : la date du jour est acceptée, et une PLAGE peut se terminer dans le futur', async () => {
      await open();
      const today = todayInParis();
      await patch({
        granularity: Granularity.Range,
        startDate: date(today),
        endDate: date(addDays(today, 3)),
      });
      expect(form().get('startDate')?.valid).toBe(true);
      expect(form().get('endDate')?.valid).toBe(true);
    });

    it('RG-01-03 : la date de fin doit être après la date de début', async () => {
      await open();
      await patch({
        granularity: Granularity.Range,
        startDate: date('2026-10-05'),
        endDate: date('2026-10-05'),
      });
      expect(error('champ-date-fin')).toContain('après la date de début');
    });

    it('RG-01-03 : 31 jours bornes incluses sont acceptés, 32 refusés', async () => {
      await open();
      await patch({
        granularity: Granularity.Range,
        startDate: date('2026-10-01'),
        endDate: date('2026-10-31'),
      });
      expect(form().get('endDate')?.valid).toBe(true);
      await patch({ endDate: date('2026-11-01') });
      expect(error('champ-date-fin')).toContain('au plus 31 jours');
    });

    it('les champs obligatoires sont signalés à l’enregistrement', async () => {
      await open();
      await fill({ startDate: null });
      await submit();
      expect(error('champ-date-debut')).toContain('obligatoire');
      expect(error('champ-heure-debut')).toContain('obligatoire');
      expect(error('champ-heure-fin')).toContain('obligatoire');
      expect(error('select-type-activite')).toContain('obligatoire');
      expect(createTimeEntry).not.toHaveBeenCalled();
    });

    it('RG-01-09 : un commentaire de 500 caractères est accepté, 501 refusé sans être tronqué', async () => {
      await open();
      await patch({ comment: 'x'.repeat(500) });
      expect(form().get('comment')?.valid).toBe(true);
      await patch({ comment: 'x'.repeat(501) });
      expect(error('champ-commentaire')).toContain('500 caractères');
      expect((form().get('comment')?.value as string).length).toBe(501);
    });

    it('« Annuler » ferme le dialogue sans appeler l’API', async () => {
      await open();
      await fill(meeting);
      el('btn-annuler-dialogue')?.querySelector('button')?.click();
      await fixture.whenStable();
      expect(fixture.componentInstance.visible()).toBe(false);
      expect(createTimeEntry).not.toHaveBeenCalled();
    });
  });

  describe('activité', () => {
    it('CA-01-01 : le 05/10/2026 de 09:00 à 10:30 en REUNION, durée de 1 h 30 affichée et saisie créée', async () => {
      createTimeEntry.mockReturnValue(of(savedEntry()));
      await open();
      await fill(meeting);
      expect(duration()).toBe('1 h 30');
      await submit();
      expect(createTimeEntry).toHaveBeenCalledWith({
        granularity: Granularity.Hour,
        startDate: '2026-10-05',
        startTime: '09:00',
        endTime: '10:30',
        activityTypeCode: 'REUNION',
      });
      expect(saved).toHaveBeenCalledWith({ mode: 'create', entry: savedEntry() });
      expect(fixture.componentInstance.visible()).toBe(false);
    });

    it('CA-01-03 : une PLAGE du lundi 05/10/2026 au vendredi 16/10/2026 en FORMATION affiche 10 jours ouvrés, soit 70 h', async () => {
      createTimeEntry.mockReturnValue(
        of(savedEntry({ granularity: Granularity.Range, endDate: '2026-10-16', durationMinutes: 4200 })),
      );
      await open();
      await fill({
        granularity: Granularity.Range,
        startDate: date('2026-10-05'),
        endDate: date('2026-10-16'),
        activityTypeCode: 'FORMATION',
      });
      expect(duration()).toBe('70 h 00 (10 jours ouvrés)');
      await submit();
      expect(createTimeEntry).toHaveBeenCalledWith({
        granularity: Granularity.Range,
        startDate: '2026-10-05',
        endDate: '2026-10-16',
        activityTypeCode: 'FORMATION',
      });
    });

    it('RG-01-08 : une JOURNEE vaut une journée standard, même un samedi ; une PLAGE sans jour ouvré vaut 0', async () => {
      await open();
      await fill({ granularity: Granularity.Day, startDate: date('2026-10-10') });
      expect(duration()).toBe('7 h 00');
      await fill({
        granularity: Granularity.Range,
        startDate: date('2026-10-10'),
        endDate: date('2026-10-11'),
      });
      expect(duration()).toBe('0 h 00 (aucun jour ouvré)');
    });

    it('CA-06-03 : la journée standard est relue à chaque ouverture, la durée d’une JOURNEE la suit (7 h 30)', async () => {
      await open();
      await fill({ granularity: Granularity.Day, startDate: date('2026-10-05') });
      expect(duration()).toBe('7 h 00');
      fixture.componentRef.setInput('visible', false);
      await fixture.whenStable();
      getAppSettings.mockReturnValue(of({ standardDayMinutes: 450, version: 1 }));
      fixture.componentRef.setInput('visible', true);
      await fixture.whenStable();
      expect(getAppSettings).toHaveBeenCalledTimes(2);
      await fill({ granularity: Granularity.Day, startDate: date('2026-10-05') });
      expect(duration()).toBe('7 h 30');
    });

    it('la durée reste « — » tant que la période est incomplète', async () => {
      await open();
      expect(duration()).toBe('—');
      await fill({ startTime: time('09:00') });
      expect(duration()).toBe('—');
    });

    it('CA-01-04 : sur un 409 RG-01-06, le dialogue reste ouvert avec la saisie, sans erreur de champ', async () => {
      createTimeEntry.mockReturnValue(
        httpError(409, {
          status: 409,
          code: 'RG-01-06',
          detail: 'Cette période chevauche une activité déjà déclarée le 05/10/2026.',
        }),
      );
      await open();
      await fill({ ...meeting, startTime: time('09:30') });
      await submit();
      expect(createTimeEntry).toHaveBeenCalledTimes(1);
      expect(fixture.componentInstance.visible()).toBe(true);
      expect(saved).not.toHaveBeenCalled();
      expect(form().get('activityTypeCode')?.value).toBe('REUNION');
      expect(document.body.querySelector('[data-cy^="erreur-"]')).toBeNull();
      // Le message de chevauchement est affiché une seule fois, par l'intercepteur d'erreurs (toast).
      expect(add).not.toHaveBeenCalled();
    });

    it('CA-01-05 : avec le type TRAVAIL_PROJET, enregistrer sans projet met le champ projet en erreur', async () => {
      await open();
      await fill({ ...meeting, activityTypeCode: 'TRAVAIL_PROJET' });
      expect(el('select-projet')).not.toBeNull();
      await submit();
      expect(error('select-projet')).toContain('Le projet est obligatoire');
      expect(createTimeEntry).not.toHaveBeenCalled();
    });

    it('RG-01-05 : avec un type qui l’exige, le projet choisi est envoyé', async () => {
      createTimeEntry.mockReturnValue(of(savedEntry()));
      await open();
      await fill({ ...meeting, activityTypeCode: 'TRAVAIL_PROJET', projectCode: 'APOLLO' });
      await submit();
      expect(createTimeEntry).toHaveBeenCalledWith(
        expect.objectContaining({ activityTypeCode: 'TRAVAIL_PROJET', projectCode: 'APOLLO' }),
      );
    });

    it('CA-01-06 : avec le type REUNION, le champ projet n’est pas affiché', async () => {
      await open();
      await fill(meeting);
      expect(el('select-projet')).toBeNull();
    });

    it('RG-01-05 : revenir à un type sans projet masque le champ et n’envoie pas le projet déjà choisi', async () => {
      createTimeEntry.mockReturnValue(of(savedEntry()));
      await open();
      await fill({ ...meeting, activityTypeCode: 'TRAVAIL_PROJET', projectCode: 'APOLLO' });
      await fill({ activityTypeCode: 'REUNION' });
      expect(el('select-projet')).toBeNull();
      await submit();
      expect(createTimeEntry.mock.calls[0][0]).not.toHaveProperty('projectCode');
    });

    it('RG-02-06 : seuls les types et projets actifs sont proposés, relus à l’ouverture', async () => {
      listActivityTypes.mockReturnValue(
        of([type(1, 'REUNION'), { ...type(2, 'ANCIEN'), active: false }]),
      );
      listProjects.mockReturnValue(of([project(1, 'APOLLO'), { ...project(2, 'CLOS'), active: false }]));
      await open();
      const dialog = fixture.componentInstance as unknown as {
        types: { activeItems(): ActivityTypeResponse[] };
        projects: { activeItems(): ProjectResponse[] };
      };
      expect(dialog.types.activeItems().map((t) => t.code)).toEqual(['REUNION']);
      expect(dialog.projects.activeItems().map((p) => p.code)).toEqual(['APOLLO']);
      expect(listActivityTypes).toHaveBeenCalledTimes(1);
      expect(listProjects).toHaveBeenCalledTimes(1);
    });

    it('le commentaire est envoyé tel que saisi, et omis s’il est vide', async () => {
      createTimeEntry.mockReturnValue(of(savedEntry()));
      await open();
      await fill({ ...meeting, comment: ' Point hebdo ' });
      await submit();
      expect(createTimeEntry.mock.calls[0][0].comment).toBe(' Point hebdo ');
    });

    it('RG-01-07 : une erreur de champ renvoyée par l’API s’affiche sous le champ, dialogue ouvert', async () => {
      createTimeEntry.mockReturnValue(
        httpError(409, {
          status: 409,
          code: 'RG-01-07',
          detail: 'Date dans le futur.',
          errors: [{ field: 'startDate', message: 'La date de début ne peut pas être dans le futur.' }],
        }),
      );
      await open();
      await fill(meeting);
      await submit();
      expect(error('champ-date-debut')).toContain('ne peut pas être dans le futur');
      expect(fixture.componentInstance.visible()).toBe(true);
      expect(add).not.toHaveBeenCalled();
    });

    it('une erreur de l’API sur un champ absent de l’écran est affichée en toast', async () => {
      createTimeEntry.mockReturnValue(
        httpError(409, {
          status: 409,
          code: 'RG-01-05',
          detail: 'Projet interdit.',
          errors: [{ field: 'projectCode', message: 'Projet interdit pour ce type.' }],
        }),
      );
      await open();
      await fill(meeting);
      await submit();
      expect(add).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'error', detail: 'Projet interdit pour ce type.' }),
      );
    });

    it('pendant l’envoi : une seule requête, champs figés, ni « Annuler », ni croix, ni Échap', async () => {
      createTimeEntry.mockReturnValue(new Subject());
      await open();
      await fill(meeting);
      await submit();
      await submit();
      expect(createTimeEntry).toHaveBeenCalledTimes(1);
      expect(el('btn-annuler-dialogue')?.querySelector('button')?.disabled).toBe(true);
      expect(document.body.querySelector('.p-dialog-close-button')).toBeNull();
      expect(el('dialogue-saisie')?.querySelector('fieldset')?.disabled).toBe(true);
      document.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }),
      );
      await fixture.whenStable();
      expect(fixture.componentInstance.visible()).toBe(true);
    });
  });

  describe('heure proposée par les sélecteurs vides', () => {
    afterEach(() => vi.useRealTimers());

    it('RG-01-01 : l’arrondi part du quart d’heure inférieur', () => {
      const at = (h: number, m: number) => new Date(2026, 9, 5, h, m, 42, 123);
      expect(toTime(quarterHourFloor(at(14, 7)))).toBe('14:00');
      expect(toTime(quarterHourFloor(at(14, 15)))).toBe('14:15');
      expect(toTime(quarterHourFloor(at(14, 59)))).toBe('14:45');
      expect(quarterHourFloor(at(14, 7)).getSeconds()).toBe(0);
    });

    it('RG-01-01 : à 14:07, les heures de début et de fin proposées sont 14:00, jamais 14:07', async () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date(2026, 9, 5, 14, 7, 30));
      await open();
      const dialog = fixture.componentInstance as unknown as {
        defaultStartTime(): Date;
        defaultEndTime(): Date;
      };
      expect(toTime(dialog.defaultStartTime())).toBe('14:00');
      expect(toTime(dialog.defaultEndTime())).toBe('14:00');
      expect(form().get('startTime')?.value).toBeNull();
    });

    it('RG-01-01 : changer l’heure de fin ne remplace pas l’heure proposée (sinon le panneau revient au début)', async () => {
      await open();
      await fill({ startTime: time('14:15') });
      const dialog = fixture.componentInstance as unknown as { defaultEndTime(): Date };
      const proposed = dialog.defaultEndTime();
      await fill({ endTime: time('15:15') });
      await fill({ endTime: time('15:30'), comment: 'autre saisie' });
      // Même objet : PrimeNG ne réinitialise son panneau que sur une nouvelle `defaultDate`.
      expect(dialog.defaultEndTime()).toBe(proposed);
      await fill({ startTime: time('10:00') });
      expect(toTime(dialog.defaultEndTime())).toBe('10:00');
    });

    it('RG-01-01 : une fois l’heure de début saisie, le sélecteur de fin part de cette heure', async () => {
      await open();
      await fill({ startTime: time('09:30') });
      const dialog = fixture.componentInstance as unknown as { defaultEndTime(): Date };
      expect(toTime(dialog.defaultEndTime())).toBe('09:30');
    });
  });

  describe('date proposée', () => {
    it('SFD-01 E1 : ouvert par le « + » d’un jour, le dialogue propose la date de ce jour', async () => {
      createTimeEntry.mockReturnValue(of(savedEntry()));
      await open(null, '2026-10-07');
      expect(form().get('startDate')?.value).toEqual(date('2026-10-07'));
      await fill({ startTime: time('09:00'), endTime: time('10:30'), activityTypeCode: 'REUNION' });
      await submit();
      expect(createTimeEntry.mock.calls[0][0].startDate).toBe('2026-10-07');
    });

    it('sans date proposée, une nouvelle saisie part de la date du jour à Paris', async () => {
      await open();
      expect(form().get('startDate')?.value).toEqual(date(todayInParis()));
    });

    it('RG-01-12 : en modification, la date de la saisie prime sur la date proposée', async () => {
      await open(savedEntry(), '2026-10-07');
      expect(form().get('startDate')?.value).toEqual(date('2026-10-05'));
    });
  });

  describe('modification', () => {
    const projectEntry = savedEntry({
      id: 12,
      granularity: Granularity.Day,
      startDate: '2026-10-06',
      startTime: null,
      endTime: null,
      activityType: {
        code: 'TRAVAIL_PROJET',
        label: 'Travail projet',
        color: TagColor.Info,
        active: true,
      },
      project: { code: 'APOLLO', name: 'Apollo', active: true },
      comment: 'Sprint 12',
      durationMinutes: 420,
      version: 4,
    });

    it('RG-01-12 : ouvre la saisie avec tous ses champs, sous le titre « Modifier la saisie »', async () => {
      await open(savedEntry({ comment: 'Point hebdo', version: 2 }));
      expect(document.body.querySelector('.p-dialog-title')?.textContent).toContain(
        'Modifier la saisie',
      );
      const value = form().getRawValue();
      expect(value.granularity).toBe(Granularity.Hour);
      expect(value.startDate).toEqual(date('2026-10-05'));
      expect((value.startTime as Date).getHours()).toBe(9);
      expect((value.endTime as Date).getHours()).toBe(10);
      expect((value.endTime as Date).getMinutes()).toBe(30);
      expect(value.activityTypeCode).toBe('REUNION');
      expect(value.comment).toBe('Point hebdo');
      expect(duration()).toBe('1 h 30');
    });

    it('RG-01-12 : le projet d’une saisie est repris et reste modifiable', async () => {
      await open(projectEntry);
      expect(el('select-projet')).not.toBeNull();
      expect(form().get('projectCode')?.enabled).toBe(true);
      expect(form().get('projectCode')?.value).toBe('APOLLO');
    });

    it('RG-01-12 : tous les champs sont modifiables, granularité comprise, et la version lue est envoyée', async () => {
      updateTimeEntry.mockReturnValue(of(projectEntry));
      await open(savedEntry({ version: 2 }));
      await fill({
        granularity: Granularity.Day,
        startDate: date('2026-10-06'),
        activityTypeCode: 'TRAVAIL_PROJET',
        projectCode: 'APOLLO',
        comment: 'Sprint 12',
      });
      await submit();
      expect(updateTimeEntry).toHaveBeenCalledWith(7, {
        granularity: Granularity.Day,
        startDate: '2026-10-06',
        activityTypeCode: 'TRAVAIL_PROJET',
        projectCode: 'APOLLO',
        comment: 'Sprint 12',
        version: 2,
      });
      expect(createTimeEntry).not.toHaveBeenCalled();
      expect(saved).toHaveBeenCalledWith({ mode: 'update', entry: projectEntry });
      expect(fixture.componentInstance.visible()).toBe(false);
    });

    it('RG-01-12 : les contrôles sont ceux de la création (heure de fin avant le début refusée)', async () => {
      await open(savedEntry());
      await fill({ endTime: time('08:00') });
      await submit();
      expect(error('champ-heure-fin')).toContain('après l’heure de début');
      expect(updateTimeEntry).not.toHaveBeenCalled();
    });

    it('RG-02-06 : un type désactivé n’est pas repris, il faut en rechoisir un actif pour enregistrer', async () => {
      await open(
        savedEntry({
          activityType: { code: 'ANCIEN', label: 'Ancien', color: TagColor.Info, active: false },
        }),
      );
      expect(form().get('activityTypeCode')?.value).toBe('');
      await submit();
      expect(error('select-type-activite')).toContain('obligatoire');
      expect(updateTimeEntry).not.toHaveBeenCalled();
    });

    it('RG-02-06 : un projet désactivé n’est pas repris, il faut en rechoisir un actif pour enregistrer', async () => {
      await open({ ...projectEntry, project: { code: 'CLOS', name: 'Clos', active: false } });
      expect(form().get('projectCode')?.value).toBeNull();
      await submit();
      expect(error('select-projet')).toContain('Le projet est obligatoire');
      expect(updateTimeEntry).not.toHaveBeenCalled();
    });

    it('modification concurrente : 409 sans code, le dialogue se ferme et la semaine est rechargée', async () => {
      updateTimeEntry.mockReturnValue(
        httpError(409, {
          status: 409,
          type: 'https://apptracer/errors/concurrent-modification',
          detail: 'Cette saisie a été modifiée entre-temps.',
        }),
      );
      await open(savedEntry());
      await submit();
      expect(conflict).toHaveBeenCalledTimes(1);
      expect(fixture.componentInstance.visible()).toBe(false);
      expect(saved).not.toHaveBeenCalled();
    });

    it('CA-01-04 : un chevauchement à la modification laisse le dialogue ouvert, sans recharger', async () => {
      updateTimeEntry.mockReturnValue(
        httpError(409, { status: 409, code: 'RG-01-06', detail: 'Chevauchement.' }),
      );
      await open(savedEntry());
      await submit();
      expect(fixture.componentInstance.visible()).toBe(true);
      expect(conflict).not.toHaveBeenCalled();
    });

    it('après une modification, une nouvelle saisie repart d’un formulaire vide', async () => {
      await open(savedEntry({ comment: 'Point hebdo' }));
      fixture.componentRef.setInput('visible', false);
      await fixture.whenStable();
      fixture.componentRef.setInput('entry', null);
      fixture.componentRef.setInput('visible', true);
      await fixture.whenStable();
      expect(document.body.querySelector('.p-dialog-title')?.textContent).toContain(
        'Nouvelle saisie',
      );
      expect(form().get('activityTypeCode')?.value).toBe('');
      expect(form().get('comment')?.value).toBe('');
      expect(form().get('startTime')?.value).toBeNull();
    });
  });
});
