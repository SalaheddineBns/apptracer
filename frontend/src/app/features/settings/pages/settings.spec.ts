import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import { AppSettingsResponse, AppSettingsService } from '../../../core/api/generated';
import {
  fromMinutes,
  standardDayValidator,
  toMinutes,
} from '../standard-day';
import { SettingsPage } from './settings';

const STANDARD_DAY_MESSAGE = 'La journée standard doit être comprise entre 1 h et 24 h, par pas de 15 minutes.';

const settings = (
  minutes: number,
  version = 0,
  over: Partial<AppSettingsResponse> = {},
): AppSettingsResponse => ({
  standardDayMinutes: minutes,
  version,
  lastModifiedAt: null,
  lastModifiedBy: undefined,
  ...over,
});

describe('SettingsPage', () => {
  const getAppSettings = vi.fn();
  const updateAppSettings = vi.fn();
  const add = vi.fn();
  let root: HTMLElement;
  let page: { form: FormGroup };
  let whenStable: () => Promise<unknown>;

  async function open(): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        { provide: AppSettingsService, useValue: { getAppSettings, updateAppSettings } },
        { provide: MessageService, useValue: { add } },
      ],
    });
    const fixture = TestBed.createComponent(SettingsPage);
    root = fixture.nativeElement as HTMLElement;
    page = fixture.componentInstance as unknown as { form: FormGroup };
    whenStable = () => fixture.whenStable();
    await whenStable();
  }

  const cy = (name: string) => root.querySelector<HTMLElement>(`[data-cy="${name}"]`);
  const button = () => cy('btn-enregistrer')?.querySelector('button') as HTMLButtonElement;
  async function enter(hours: number | null, minutes: number | null): Promise<void> {
    page.form.setValue({ hours, minutes });
    page.form.markAsDirty();
    await whenStable();
  }
  async function submit(): Promise<void> {
    (cy('form-parametres') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await whenStable();
  }

  beforeEach(() => {
    for (const fn of [getAppSettings, updateAppSettings, add]) {
      fn.mockReset();
    }
    TestBed.resetTestingModule();
  });

  it('état de chargement : squelette tant que la valeur n’est pas arrivée', async () => {
    getAppSettings.mockReturnValue(new Subject());
    await open();
    expect(cy('etat-chargement')).not.toBeNull();
    expect(cy('form-parametres')).toBeNull();
  });

  it('état d’erreur : message et « Réessayer » qui recharge la valeur', async () => {
    getAppSettings.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })),
    );
    await open();
    expect(cy('etat-erreur')?.textContent).toContain('Panne serveur');
    getAppSettings.mockReturnValue(of(settings(480)));
    (cy('btn-reessayer')?.querySelector('button') as HTMLButtonElement).click();
    await whenStable();
    expect(cy('form-parametres')).not.toBeNull();
    expect(cy('etat-erreur')).toBeNull();
  });

  it('état nominal : la valeur du back est affichée en heures et minutes (480 min = 8 h 00), jamais modifiée', async () => {
    getAppSettings.mockReturnValue(of(settings(480)));
    await open();
    expect(getAppSettings).toHaveBeenCalledTimes(1);
    expect(page.form.getRawValue()).toEqual({ hours: 8, minutes: 0 });
    expect(root.querySelector('label[for="standard-day-hours"]')?.textContent).toContain('Heures');
    expect(root.querySelector('label[for="standard-day-minutes"]')?.textContent).toContain(
      'Minutes',
    );
    expect(cy('derniere-modification')?.textContent).toContain('Jamais modifiée');
    expect(cy('aide-parametres')?.textContent).toContain('ne sont pas recalculées');
  });

  it('RG-06-06 : « Enregistrer » est inactif tant que la valeur n’a pas changé, sans requête', async () => {
    getAppSettings.mockReturnValue(of(settings(480)));
    await open();
    expect(button().disabled).toBe(true);
    await submit();
    expect(updateAppSettings).not.toHaveBeenCalled();
    await enter(7, 30);
    expect(button().disabled).toBe(false);
    await enter(8, 0);
    expect(button().disabled).toBe(true);
  });

  it('CA-06-02 : enregistrer 7 h 30 envoie 450 minutes, affiche la valeur et la dernière modification (heure de Paris)', async () => {
    getAppSettings.mockReturnValue(of(settings(480, 2)));
    updateAppSettings.mockReturnValue(
      of(
        settings(450, 3, {
          lastModifiedAt: '2026-10-12T12:05:00Z',
          lastModifiedBy: { id: 1, firstName: 'Ada', lastName: 'Martin' },
        }),
      ),
    );
    await open();
    await enter(7, 30);
    await submit();
    expect(updateAppSettings.mock.calls[0][0]).toEqual({ standardDayMinutes: 450, version: 2 });
    expect(page.form.getRawValue()).toEqual({ hours: 7, minutes: 30 });
    expect(cy('derniere-modification')?.textContent).toContain('Ada Martin');
    expect(cy('derniere-modification')?.textContent).toContain('12/10/2026 à 14:05');
    expect(add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'success',
        detail: 'Journée standard : 7 h 30.',
        life: 3000,
      }),
    );
  });

  it('CA-06-05 : 7 h 10 met le champ en erreur et ne lance aucune requête', async () => {
    getAppSettings.mockReturnValue(of(settings(480)));
    await open();
    await enter(7, 10);
    await submit();
    expect(updateAppSettings).not.toHaveBeenCalled();
    expect(cy('erreur-duree')?.textContent).toContain(STANDARD_DAY_MESSAGE);
  });

  it('un 400 du back sur standardDayMinutes s’affiche sous le champ, sans toast', async () => {
    getAppSettings.mockReturnValue(of(settings(480)));
    updateAppSettings.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              detail: 'Invalide',
              errors: [{ field: 'standardDayMinutes', message: 'Message du back' }],
            },
          }),
      ),
    );
    await open();
    await enter(7, 30);
    await submit();
    expect(cy('erreur-duree')?.textContent).toContain('Message du back');
    expect(add).not.toHaveBeenCalled();
  });

  it('modification concurrente (409) : message clair et le formulaire reprend la valeur rechargée', async () => {
    getAppSettings
      .mockReturnValueOnce(of(settings(480, 0)))
      .mockReturnValueOnce(of(settings(480, 1)));
    updateAppSettings.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { detail: 'Conflit' } })),
    );
    await open();
    await enter(7, 30);
    await submit();
    expect(cy('message-conflit')?.textContent).toContain('Un autre administrateur');
    expect(page.form.getRawValue()).toEqual({ hours: 8, minutes: 0 });
    expect(add).not.toHaveBeenCalled();
  });

  it('clics rapprochés : une seule requête, bouton en chargement pendant l’appel', async () => {
    getAppSettings.mockReturnValue(of(settings(480)));
    updateAppSettings.mockReturnValue(new Subject());
    await open();
    await enter(7, 30);
    await submit();
    await submit();
    await submit();
    expect(updateAppSettings).toHaveBeenCalledTimes(1);
    expect(button().disabled).toBe(true);
  });

  const input = (name: 'champ-heures' | 'champ-minutes') => cy(name) as HTMLInputElement;
  async function typeInto(name: 'champ-heures' | 'champ-minutes', text: string): Promise<void> {
    const field = input(name);
    field.focus();
    field.value = text;
    field.dispatchEvent(new Event('input', { bubbles: true }));
    field.dispatchEvent(new Event('blur'));
    await whenStable();
  }
  async function press(key: string): Promise<void> {
    input('champ-minutes').dispatchEvent(
      new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }),
    );
    await whenStable();
  }

  it('F2 : une valeur tapée hors bornes n’est jamais ramenée en silence, elle reste en erreur sans requête', async () => {
    getAppSettings.mockReturnValue(of(settings(480)));
    await open();
    await typeInto('champ-heures', '75');
    expect(page.form.getRawValue().hours).toBe(75);
    await submit();
    expect(updateAppSettings).not.toHaveBeenCalled();
    expect(cy('erreur-duree')?.textContent).toContain(STANDARD_DAY_MESSAGE);
    await typeInto('champ-heures', '-5');
    expect(page.form.getRawValue().hours).toBe(-5);
    await typeInto('champ-minutes', '-15');
    expect(page.form.getRawValue().minutes).toBe(-15);
  });

  it('F1 : une minute tapée à 50 reste 50 et donne l’erreur de validation, jamais 45', async () => {
    getAppSettings.mockReturnValue(of(settings(480)));
    await open();
    await typeInto('champ-minutes', '50');
    expect(page.form.getRawValue().minutes).toBe(50);
    await submit();
    expect(updateAppSettings).not.toHaveBeenCalled();
    expect(cy('erreur-duree')).not.toBeNull();
  });

  it('F1 : les flèches des minutes ne donnent que 0, 15, 30 et 45', async () => {
    getAppSettings.mockReturnValue(of(settings(480)));
    await open();
    const seen: (number | null)[] = [];
    for (let i = 0; i < 5; i++) {
      await press('ArrowUp');
      seen.push(page.form.getRawValue().minutes);
    }
    for (let i = 0; i < 5; i++) {
      await press('ArrowDown');
      seen.push(page.form.getRawValue().minutes);
    }
    expect(seen).toEqual([15, 30, 45, 45, 45, 30, 15, 0, 0, 0]);
  });

  it('F3 : un 409 suivi d’un rechargement en échec le dit (état d’erreur, « Réessayer »), sans prétendre afficher la valeur actuelle', async () => {
    getAppSettings
      .mockReturnValueOnce(of(settings(480, 0)))
      .mockReturnValueOnce(
        throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne' } })),
      );
    updateAppSettings.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { detail: 'Conflit' } })),
    );
    await open();
    await enter(7, 30);
    await submit();
    expect(cy('etat-erreur')?.textContent).toContain('n’a pas pu être rechargée');
    expect(cy('message-conflit')).toBeNull();
    expect(cy('form-parametres')).toBeNull();
    getAppSettings.mockReturnValue(of(settings(480, 1)));
    (cy('btn-reessayer')?.querySelector('button') as HTMLButtonElement).click();
    await whenStable();
    expect(cy('etat-erreur')).toBeNull();
    expect(cy('message-conflit')).not.toBeNull();
    expect(page.form.getRawValue()).toEqual({ hours: 8, minutes: 0 });
  });

  it('F4 : les champs sont en lecture seule pendant l’envoi', async () => {
    getAppSettings.mockReturnValue(of(settings(480)));
    const pending = new Subject<AppSettingsResponse>();
    updateAppSettings.mockReturnValue(pending);
    await open();
    expect(input('champ-heures').readOnly).toBe(false);
    await enter(7, 30);
    await submit();
    expect(input('champ-heures').readOnly).toBe(true);
    expect(input('champ-minutes').readOnly).toBe(true);
    pending.next(settings(450, 1));
    pending.complete();
    await whenStable();
    expect(input('champ-heures').readOnly).toBe(false);
  });

  it('dernière modification sans auteur renvoyé : la date seule, aucun auteur inventé', async () => {
    getAppSettings.mockReturnValue(
      of(
        settings(450, 1, { lastModifiedAt: '2026-01-15T08:00:00Z', lastModifiedBy: null as never }),
      ),
    );
    await open();
    expect(cy('derniere-modification')?.textContent).toContain('le 15/01/2026 à 09:00');
  });
});

describe('standard-day (RG-06-02)', () => {
  const check = (hours: number | null, minutes: number | null) =>
    standardDayValidator({ value: { hours, minutes } } as never);

  it.each([
    [1, 0],
    [24, 0],
    [7, 30],
    [7, 0],
  ])('%i h %i accepté', (h, m) => expect(check(h, m)).toBeNull());

  it.each([
    [0, 45],
    [24, 15],
    [7, 10],
    [0, 0],
    [7, 75],
    [-1, 0],
    [null, 30],
    [7, null],
    [7.5, 0],
  ])('%s h %s refusé', (h, m) => expect(check(h, m)).not.toBeNull());

  it('conversions heures/minutes', () => {
    expect(toMinutes({ hours: 7, minutes: 30 })).toBe(450);
    expect(fromMinutes(480)).toEqual({ hours: 8, minutes: 0 });
  });
});
