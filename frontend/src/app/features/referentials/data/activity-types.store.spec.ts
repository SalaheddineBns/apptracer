import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ActivityTypeResponse, ActivityTypesService, TagColor } from '../../../core/api/generated';
import { ActivityTypesStore } from './activity-types.store';

function type(id: number, label: string, active = true): ActivityTypeResponse {
  return {
    id,
    code: `T${id}`,
    label,
    projectRequired: false,
    color: TagColor.Info,
    active,
    version: 0,
  };
}

describe('ActivityTypesStore', () => {
  const api = {
    listActivityTypes: vi.fn(),
    createActivityType: vi.fn(),
    updateActivityType: vi.fn(),
    deactivateActivityType: vi.fn(),
    reactivateActivityType: vi.fn(),
  };

  function setup(): ActivityTypesStore {
    TestBed.configureTestingModule({
      providers: [{ provide: ActivityTypesService, useValue: api }],
    });
    return TestBed.inject(ActivityTypesStore);
  }

  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset());
  });

  it('RG-02-10 : charge la liste et conserve l’ordre renvoyé par le back', () => {
    api.listActivityTypes.mockReturnValue(of([type(2, 'Zèbre'), type(1, 'Alpha')]));
    const store = setup();
    store.load();
    expect(store.items().map((i) => i.label)).toEqual(['Zèbre', 'Alpha']);
    expect(store.loaded()).toBe(true);
    expect(store.loading()).toBe(false);
  });

  it('RG-02-09 : expose l’erreur sans toast et ne marque pas la liste chargée', () => {
    api.listActivityTypes.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })),
    );
    const store = setup();
    store.load();
    expect(store.error()).toBe('Panne serveur');
    expect(store.loaded()).toBe(false);
    expect(store.loading()).toBe(false);
  });

  it('RG-02-06 : activeItems ne garde que les types actifs, dans l’ordre du back', () => {
    api.listActivityTypes.mockReturnValue(of([type(1, 'A'), type(2, 'B', false), type(3, 'C')]));
    const store = setup();
    store.load();
    expect(store.activeItems().map((i) => i.id)).toEqual([1, 3]);
    expect(store.items()).toHaveLength(3);
  });

  it('ensureLoaded : une seule requête, même appelé plusieurs fois', () => {
    api.listActivityTypes.mockReturnValue(of([type(1, 'A')]));
    const store = setup();
    store.ensureLoaded();
    store.ensureLoaded();
    expect(api.listActivityTypes).toHaveBeenCalledTimes(1);
  });

  it('après création : recharge la liste pour respecter l’ordre du back', () => {
    api.listActivityTypes
      .mockReturnValueOnce(of([type(1, 'B')]))
      .mockReturnValueOnce(of([type(2, 'A'), type(1, 'B')]));
    api.createActivityType.mockReturnValue(of(type(2, 'A')));
    const store = setup();
    store.load();
    store
      .create({ code: 'T2', label: 'A', projectRequired: false, color: TagColor.Info })
      .subscribe();
    expect(store.items().map((i) => i.id)).toEqual([2, 1]);
  });

  it('après modification : recharge la liste (le libellé change l’ordre)', () => {
    api.listActivityTypes
      .mockReturnValueOnce(of([type(1, 'A'), type(2, 'B')]))
      .mockReturnValueOnce(of([type(2, 'B'), type(1, 'Z')]));
    api.updateActivityType.mockReturnValue(of(type(1, 'Z')));
    const store = setup();
    store.load();
    store
      .update(1, { label: 'Z', projectRequired: false, color: TagColor.Info, version: 0 })
      .subscribe();
    expect(store.items().map((i) => i.label)).toEqual(['B', 'Z']);
  });

  it('désactivation puis réactivation : remplace la ligne en place sans rechargement', () => {
    api.listActivityTypes.mockReturnValue(of([type(1, 'A'), type(2, 'B')]));
    api.deactivateActivityType.mockReturnValue(of(type(1, 'A', false)));
    api.reactivateActivityType.mockReturnValue(of(type(1, 'A', true)));
    const store = setup();
    store.load();
    store.deactivate(1).subscribe();
    expect(store.items().map((i) => i.active)).toEqual([false, true]);
    expect(store.activeItems().map((i) => i.id)).toEqual([2]);
    store.reactivate(1).subscribe();
    expect(store.items().map((i) => i.active)).toEqual([true, true]);
    expect(api.listActivityTypes).toHaveBeenCalledTimes(1);
  });
});
