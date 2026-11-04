import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import { AppSettingsResponse, AppSettingsService } from '../api/generated';
import { AppSettingsStore } from './app-settings.store';

function settings(
  minutes: number,
  version = 0,
  extra: Partial<AppSettingsResponse> = {},
): AppSettingsResponse {
  return { standardDayMinutes: minutes, version, lastModifiedAt: null, ...extra };
}

describe('AppSettingsStore', () => {
  const api = { getAppSettings: vi.fn(), updateAppSettings: vi.fn() };
  const messages = { add: vi.fn() };

  function setup(): AppSettingsStore {
    TestBed.configureTestingModule({
      providers: [
        { provide: AppSettingsService, useValue: api },
        { provide: MessageService, useValue: messages },
      ],
    });
    return TestBed.inject(AppSettingsStore);
  }

  beforeEach(() => {
    api.getAppSettings.mockReset();
    api.updateAppSettings.mockReset();
    messages.add.mockReset();
  });

  it('CA-06-01 : charge la journée standard et l’expose en minutes', () => {
    api.getAppSettings.mockReturnValue(of(settings(480)));
    const store = setup();
    expect(store.standardDayMinutes()).toBeNull();
    store.load();
    expect(store.standardDayMinutes()).toBe(480);
    expect(store.loaded()).toBe(true);
    expect(store.loading()).toBe(false);
  });

  it('RG-06-05 : lastModifiedBy null ou absent donne null', () => {
    api.getAppSettings.mockReturnValue(of(settings(480, 0, { lastModifiedBy: null as never })));
    const store = setup();
    store.load();
    expect(store.lastModifiedBy()).toBeNull();
    expect(store.lastModifiedAt()).toBeNull();
    api.getAppSettings.mockReturnValue(
      of(
        settings(450, 1, {
          lastModifiedAt: '2026-10-12T08:00:00Z',
          lastModifiedBy: { id: 1, firstName: 'A', lastName: 'B' },
        }),
      ),
    );
    store.load();
    expect(store.lastModifiedBy()?.lastName).toBe('B');
    expect(store.lastModifiedAt()).toBe('2026-10-12T08:00:00Z');
  });

  it('expose l’erreur de chargement sans toast et ne marque pas la valeur chargée', () => {
    api.getAppSettings.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })),
    );
    const store = setup();
    store.load();
    expect(store.error()).toBe('Panne serveur');
    expect(store.loaded()).toBe(false);
    expect(store.loading()).toBe(false);
    expect(messages.add).not.toHaveBeenCalled();
  });

  it('ensureLoaded : une seule requête, chargée ou en cours ; load() force un rechargement', () => {
    const pending = new Subject<AppSettingsResponse>();
    api.getAppSettings.mockReturnValue(pending);
    const store = setup();
    store.ensureLoaded();
    store.ensureLoaded();
    expect(api.getAppSettings).toHaveBeenCalledTimes(1);
    pending.next(settings(480));
    pending.complete();
    store.ensureLoaded();
    expect(api.getAppSettings).toHaveBeenCalledTimes(1);
    api.getAppSettings.mockReturnValue(of(settings(450, 1)));
    store.load();
    expect(api.getAppSettings).toHaveBeenCalledTimes(2);
    expect(store.standardDayMinutes()).toBe(450);
  });

  it('CA-06-02 : la modification envoie la valeur et la version, puis porte la réponse du back', () => {
    api.getAppSettings.mockReturnValue(of(settings(480, 3)));
    api.updateAppSettings.mockReturnValue(
      of(
        settings(450, 4, {
          lastModifiedAt: '2026-10-12T08:00:00Z',
          lastModifiedBy: { id: 1, firstName: 'A', lastName: 'B' },
        }),
      ),
    );
    const store = setup();
    store.load();
    store.update(450).subscribe();
    expect(api.updateAppSettings.mock.calls[0][0]).toEqual({ standardDayMinutes: 450, version: 3 });
    expect(store.standardDayMinutes()).toBe(450);
    expect(store.settings()?.version).toBe(4);
    expect(store.lastModifiedBy()?.firstName).toBe('A');
    expect(store.saving()).toBe(false);
  });

  it('CA-06-06 : un 400 sur le champ est relayé sans toast ni rechargement', () => {
    const fieldError = new HttpErrorResponse({
      status: 400,
      error: {
        detail: 'Invalide',
        errors: [{ field: 'standardDayMinutes', message: 'Pas de 15 minutes' }],
      },
    });
    api.getAppSettings.mockReturnValue(of(settings(480)));
    api.updateAppSettings.mockReturnValue(throwError(() => fieldError));
    const store = setup();
    store.load();
    const onError = vi.fn();
    store.update(430).subscribe({ error: onError });
    expect(onError).toHaveBeenCalledWith(fieldError);
    expect(messages.add).not.toHaveBeenCalled();
    expect(api.getAppSettings).toHaveBeenCalledTimes(1);
    expect(store.standardDayMinutes()).toBe(480);
    expect(store.saving()).toBe(false);
  });

  it('modification concurrente (409) : relayée sans toast, le store se recharge', () => {
    const conflict = new HttpErrorResponse({
      status: 409,
      error: { detail: 'Modifié entre-temps' },
    });
    api.getAppSettings
      .mockReturnValueOnce(of(settings(480, 0)))
      .mockReturnValueOnce(of(settings(480, 1)));
    api.updateAppSettings.mockReturnValue(throwError(() => conflict));
    const store = setup();
    store.load();
    const onError = vi.fn();
    store.update(450).subscribe({ error: onError });
    expect(onError).toHaveBeenCalledWith(conflict);
    expect(messages.add).not.toHaveBeenCalled();
    expect(api.getAppSettings).toHaveBeenCalledTimes(2);
    expect(store.standardDayMinutes()).toBe(480);
    expect(store.settings()?.version).toBe(1);
  });

  it('une autre erreur (500) est notifiée par un toast puis relayée', () => {
    api.getAppSettings.mockReturnValue(of(settings(480)));
    api.updateAppSettings.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })),
    );
    const store = setup();
    store.load();
    const onError = vi.fn();
    store.update(450).subscribe({ error: onError });
    expect(onError).toHaveBeenCalled();
    expect(messages.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'Panne serveur' }),
    );
  });

  it('requêtes rapprochées : une seule modification part tant que la réponse n’est pas arrivée', () => {
    const pending = new Subject<AppSettingsResponse>();
    api.getAppSettings.mockReturnValue(of(settings(480)));
    api.updateAppSettings.mockReturnValue(pending);
    const store = setup();
    store.load();
    store.update(450).subscribe();
    store.update(450).subscribe();
    store.update(480).subscribe();
    expect(api.updateAppSettings).toHaveBeenCalledTimes(1);
    expect(store.saving()).toBe(true);
    pending.next(settings(450, 1));
    pending.complete();
    expect(store.saving()).toBe(false);
  });

  it('une modification avant le premier chargement n’envoie rien', () => {
    const store = setup();
    store.update(450).subscribe();
    expect(api.updateAppSettings).not.toHaveBeenCalled();
  });
});
