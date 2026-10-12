import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ProjectResponse, ProjectsService } from '../../../core/api/generated';
import { ProjectsStore } from './projects.store';

function project(id: number, name: string, active = true): ProjectResponse {
  return { id, code: `T${id}`, name, active, version: 0 };
}

describe('ProjectsStore', () => {
  const api = {
    listProjects: vi.fn(),
    createProject: vi.fn(),
    updateProject: vi.fn(),
    deactivateProject: vi.fn(),
    reactivateProject: vi.fn(),
  };

  function setup(): ProjectsStore {
    TestBed.configureTestingModule({ providers: [{ provide: ProjectsService, useValue: api }] });
    return TestBed.inject(ProjectsStore);
  }

  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset());
  });

  it('RG-02-10 : charge la liste et conserve l’ordre renvoyé par le back', () => {
    api.listProjects.mockReturnValue(of([project(2, 'Zèbre'), project(1, 'Alpha')]));
    const store = setup();
    store.load();
    expect(store.items().map((i) => i.name)).toEqual(['Zèbre', 'Alpha']);
    expect(store.loaded()).toBe(true);
    expect(store.loading()).toBe(false);
  });

  it('RG-02-09 : expose l’erreur sans toast et ne marque pas la liste chargée', () => {
    api.listProjects.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })),
    );
    const store = setup();
    store.load();
    expect(store.error()).toBe('Panne serveur');
    expect(store.loaded()).toBe(false);
    expect(store.loading()).toBe(false);
  });

  it('RG-02-06 : activeItems ne garde que les projets actifs, dans l’ordre du back', () => {
    api.listProjects.mockReturnValue(
      of([project(1, 'A'), project(2, 'B', false), project(3, 'C')]),
    );
    const store = setup();
    store.load();
    expect(store.activeItems().map((i) => i.id)).toEqual([1, 3]);
    expect(store.items()).toHaveLength(3);
  });

  it('ensureLoaded : une seule requête, même appelé plusieurs fois', () => {
    api.listProjects.mockReturnValue(of([project(1, 'A')]));
    const store = setup();
    store.ensureLoaded();
    store.ensureLoaded();
    expect(api.listProjects).toHaveBeenCalledTimes(1);
  });

  it('après création : recharge la liste pour respecter l’ordre du back', () => {
    api.listProjects
      .mockReturnValueOnce(of([project(1, 'B')]))
      .mockReturnValueOnce(of([project(2, 'A'), project(1, 'B')]));
    api.createProject.mockReturnValue(of(project(2, 'A')));
    const store = setup();
    store.load();
    store.create({ code: 'T2', name: 'A' }).subscribe();
    expect(store.items().map((i) => i.id)).toEqual([2, 1]);
  });

  it('après modification : recharge la liste (le libellé change l’ordre)', () => {
    api.listProjects
      .mockReturnValueOnce(of([project(1, 'A'), project(2, 'B')]))
      .mockReturnValueOnce(of([project(2, 'B'), project(1, 'Z')]));
    api.updateProject.mockReturnValue(of(project(1, 'Z')));
    const store = setup();
    store.load();
    store.update(1, { name: 'Z', version: 0 }).subscribe();
    expect(store.items().map((i) => i.name)).toEqual(['B', 'Z']);
  });

  it('désactivation puis réactivation : remplace la ligne en place sans rechargement', () => {
    api.listProjects.mockReturnValue(of([project(1, 'A'), project(2, 'B')]));
    api.deactivateProject.mockReturnValue(of(project(1, 'A', false)));
    api.reactivateProject.mockReturnValue(of(project(1, 'A', true)));
    const store = setup();
    store.load();
    store.deactivate(1).subscribe();
    expect(store.items().map((i) => i.active)).toEqual([false, true]);
    expect(store.activeItems().map((i) => i.id)).toEqual([2]);
    store.reactivate(1).subscribe();
    expect(store.items().map((i) => i.active)).toEqual([true, true]);
    expect(api.listProjects).toHaveBeenCalledTimes(1);
  });
});
