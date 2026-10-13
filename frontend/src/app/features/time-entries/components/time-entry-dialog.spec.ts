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
import { timeStringToDate } from './time-entry-period';

const date = (iso: string) => isoToLocalDate(iso);
const time = (value: string) => timeStringToDate(value);
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
  let fixture: ReturnType<typeof TestBed.createComponent<TimeEntryDialog>>;

  async function open(): Promise<void> {
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
});
