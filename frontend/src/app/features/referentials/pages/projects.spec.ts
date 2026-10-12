import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import { ProjectResponse, ProjectsService } from '../../../core/api/generated';
import { ProjectsStore } from '../data/projects.store';
import { ProjectsPage } from './projects';

const project = (id: number, over: Partial<ProjectResponse> = {}): ProjectResponse => ({
  id,
  code: `PROJ_${id}`,
  name: `Projet ${id}`,
  active: true,
  version: 0,
  ...over,
});

describe('ProjectsPage', () => {
  const listProjects = vi.fn();
  const deactivateProject = vi.fn();
  const reactivateProject = vi.fn();
  const confirm = vi.fn();
  const add = vi.fn();
  let root: HTMLElement;
  let fixtureRef: ReturnType<typeof TestBed.createComponent<ProjectsPage>>;
  let whenStable: () => Promise<unknown>;

  async function open(): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        { provide: ProjectsService, useValue: { listProjects, deactivateProject, reactivateProject } },
        { provide: ConfirmationService, useValue: { confirm } },
        { provide: MessageService, useValue: { add } },
      ],
    });
    const fixture = TestBed.createComponent(ProjectsPage);
    fixtureRef = fixture;
    root = fixture.nativeElement as HTMLElement;
    whenStable = () => fixture.whenStable();
    await whenStable();
  }

  const cy = (name: string) => root.querySelector<HTMLElement>(`[data-cy="${name}"]`);
  const click = async (name: string) => {
    (cy(name)?.querySelector('button') ?? cy(name))?.click();
    await whenStable();
  };

  beforeEach(() => {
    // jsdom n'a pas ResizeObserver, utilisé par la colonne figée (pFrozenColumn).
    vi.stubGlobal('ResizeObserver', class { observe = vi.fn(); unobserve = vi.fn(); disconnect = vi.fn(); });
    for (const fn of [listProjects, deactivateProject, reactivateProject, confirm, add]) {
      fn.mockReset();
    }
    TestBed.resetTestingModule();
  });

  it('affiche l’état de chargement tant que la liste n’est pas arrivée', async () => {
    listProjects.mockReturnValue(new Subject());
    await open();
    expect(cy('etat-chargement')).not.toBeNull();
    expect(cy('table-projets')).toBeNull();
  });

  it('affiche l’état vide quand il n’y a aucun projet', async () => {
    listProjects.mockReturnValue(of([]));
    await open();
    expect(cy('etat-vide')).not.toBeNull();
  });

  it('affiche l’état d’erreur avec « Réessayer », qui recharge la liste', async () => {
    listProjects.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })));
    await open();
    expect(cy('etat-erreur')?.textContent).toContain('Panne serveur');
    listProjects.mockReturnValue(of([project(1)]));
    await click('btn-reessayer');
    expect(cy('table-projets')).not.toBeNull();
    expect(cy('etat-erreur')).toBeNull();
  });

  it('état nominal : une seule requête, ordre du back, code, nom et statut en toutes lettres', async () => {
    listProjects.mockReturnValue(of([project(2, { name: 'Zèbre' }), project(1, { name: 'Alpha', active: false })]));
    await open();
    expect(listProjects).toHaveBeenCalledTimes(1);
    const rows = Array.from(root.querySelectorAll('[data-cy^="ligne-projet-"]')).map((r) => r.getAttribute('data-cy'));
    expect(rows).toEqual(['ligne-projet-2', 'ligne-projet-1']);
    expect(cy('ligne-projet-2')?.textContent).toContain('PROJ_2');
    expect(cy('ligne-projet-2')?.textContent).toContain('Zèbre');
    expect(cy('statut-projet-1')?.textContent).toContain('Inactif');
    expect(cy('statut-projet-2')?.textContent).toContain('Actif');
  });

  it('RG-02-05 : la désactivation demande confirmation (conséquence de RG-02-06), annulée elle n’appelle pas l’API', async () => {
    listProjects.mockReturnValue(of([project(1, { name: 'Alpha' })]));
    await open();
    await click('btn-desactiver-projet-1');
    expect(confirm).toHaveBeenCalledTimes(1);
    const options = confirm.mock.calls[0][0];
    expect(options.message).toContain('Alpha');
    expect(options.message).toContain('plus proposé pour une nouvelle saisie ni pour la modification d\'une saisie');
    expect(options.message).toContain('saisies existantes sont conservées');
    expect(options.closable).not.toBe(false);
    expect(deactivateProject).not.toHaveBeenCalled();
    expect(cy('statut-projet-1')?.textContent).toContain('Actif');
  });

  it('CA-02-06 : après désactivation, la ligne reste affichée « Inactif » pour l’ADMIN et le projet quitte activeItems', async () => {
    listProjects.mockReturnValue(of([project(1), project(2)]));
    deactivateProject.mockReturnValue(of(project(1, { active: false })));
    await open();
    await click('btn-desactiver-projet-1');
    confirm.mock.calls[0][0].accept();
    await whenStable();
    expect(deactivateProject).toHaveBeenCalledWith(1);
    expect(cy('ligne-projet-1')).not.toBeNull();
    expect(cy('statut-projet-1')?.textContent).toContain('Inactif');
    expect(cy('btn-reactiver-projet-1')).not.toBeNull();
    expect(TestBed.inject(ProjectsStore).activeItems().map((p) => p.id)).toEqual([2]);
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', life: 3000 }));
    expect(listProjects).toHaveBeenCalledTimes(1);
  });

  it('CA-02-08 : la réactivation est directe, remet « Actif » et le projet revient dans activeItems', async () => {
    listProjects.mockReturnValue(of([project(1, { active: false }), project(2)]));
    reactivateProject.mockReturnValue(of(project(1)));
    await open();
    const store = TestBed.inject(ProjectsStore);
    expect(store.activeItems().map((p) => p.id)).toEqual([2]);
    await click('btn-reactiver-projet-1');
    expect(confirm).not.toHaveBeenCalled();
    expect(reactivateProject).toHaveBeenCalledWith(1);
    expect(cy('statut-projet-1')?.textContent).toContain('Actif');
    expect(store.activeItems().map((p) => p.id)).toEqual([1, 2]);
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', life: 3000 }));
  });

  it('« Nouveau projet » (en-tête et état vide) et « Modifier » ouvrent le dialogue E4', async () => {
    listProjects.mockReturnValue(of([project(1, { name: 'Alpha' })]));
    await open();
    expect(cy('btn-modifier-projet-1')?.querySelector('button')?.getAttribute('aria-label')).toBe('Modifier le projet Alpha');
    await click('btn-nouveau-projet');
    const page = fixtureRef.componentInstance as unknown as { dialogVisible(): boolean; editing(): unknown };
    expect(page.dialogVisible()).toBe(true);
    expect(page.editing()).toBeNull();
    await click('btn-modifier-projet-1');
    expect(page.editing()).toEqual(expect.objectContaining({ id: 1 }));
  });

  it('l’état vide propose « Nouveau projet »', async () => {
    listProjects.mockReturnValue(of([]));
    await open();
    expect(cy('btn-nouveau-projet-vide')).not.toBeNull();
  });

  it('le toast de création a le titre « Projet créé », celui d’une modification « Projet mis à jour »', async () => {
    listProjects.mockReturnValue(of([]));
    await open();
    const page = fixtureRef.componentInstance as unknown as { onSaved(e: { mode: 'create' | 'update'; project: ProjectResponse }): void };
    page.onSaved({ mode: 'create', project: project(1) });
    page.onSaved({ mode: 'update', project: project(1) });
    expect(add.mock.calls.map((c) => c[0].summary)).toEqual(['Projet créé', 'Projet mis à jour']);
  });

  it('les actions par ligne nomment le projet dans leur aria-label', async () => {
    listProjects.mockReturnValue(of([project(1, { name: 'Alpha' })]));
    await open();
    expect(cy('btn-desactiver-projet-1')?.querySelector('button')?.getAttribute('aria-label')).toBe('Désactiver le projet Alpha');
  });
});
