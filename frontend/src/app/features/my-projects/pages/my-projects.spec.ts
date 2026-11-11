import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { MyProjectsResponse, ProjectResponse, ProjectsService } from '../../../core/api/generated';
import { MyProjectsPage } from './my-projects';

const project = (id: number, code: string, name: string): ProjectResponse => ({ id, code, name, active: true, version: 0 });
const alpha = project(1, 'ALPHA', 'Alpha');
const commun = project(3, 'COMMUN', 'Commun');

describe('MyProjectsPage', () => {
  const listMyProjects = vi.fn();
  let fixture: ReturnType<typeof TestBed.createComponent<MyProjectsPage>>;
  let root: HTMLElement;

  async function open(): Promise<void> {
    TestBed.configureTestingModule({ providers: [{ provide: ProjectsService, useValue: { listMyProjects } }] });
    fixture = TestBed.createComponent(MyProjectsPage);
    root = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  const cy = (sel: string) => root.querySelector<HTMLElement>(`[data-cy="${sel}"]`);
  const codes = (sel: string) =>
    Array.from(cy(sel)?.querySelectorAll('[data-cy^="mon-projet-"]') ?? []).map((n) => n.getAttribute('data-cy'));

  beforeEach(() => {
    listMyProjects.mockReset();
    TestBed.resetTestingModule();
  });

  it('CA-10-09 : Alice voit le groupe de l’équipe Dev avec ALPHA, puis les projets communs avec COMMUN', async () => {
    listMyProjects.mockReturnValue(
      of<MyProjectsResponse>({ teams: [{ team: { id: 7, name: 'Dev' }, projects: [alpha] }], sharedProjects: [commun] }),
    );
    await open();
    expect(cy('groupe-equipe-7')?.querySelector('h2')?.textContent).toContain('Projets de l’équipe Dev');
    expect(codes('groupe-equipe-7')).toEqual(['mon-projet-ALPHA']);
    expect(cy('mon-projet-ALPHA')?.textContent).toContain('Alpha');
    expect(codes('groupe-projets-communs')).toEqual(['mon-projet-COMMUN']);
    expect(cy('aucune-equipe')).toBeNull();
    const groups = Array.from(root.querySelectorAll('[data-cy^="groupe-"]')).map((n) => n.getAttribute('data-cy'));
    expect(groups).toEqual(['groupe-equipe-7', 'groupe-projets-communs']);
  });

  it('CA-10-09 : Chloé, sans équipe, voit le message « aucune équipe » puis les projets communs', async () => {
    listMyProjects.mockReturnValue(of<MyProjectsResponse>({ teams: [], sharedProjects: [commun] }));
    await open();
    expect(cy('aucune-equipe')?.textContent).toContain('Vous ne faites partie d’aucune équipe.');
    expect(codes('groupe-projets-communs')).toEqual(['mon-projet-COMMUN']);
  });

  it('RG-10-11 : une équipe sans projet actif affiche « Aucun projet pour cette équipe »', async () => {
    listMyProjects.mockReturnValue(
      of<MyProjectsResponse>({ teams: [{ team: { id: 7, name: 'Dev' }, projects: [] }], sharedProjects: [commun] }),
    );
    await open();
    expect(cy('groupe-equipe-7')?.querySelector('[data-cy="equipe-sans-projet"]')?.textContent).toContain(
      'Aucun projet pour cette équipe.',
    );
  });

  it('état vide : aucun projet actif nulle part', async () => {
    listMyProjects.mockReturnValue(
      of<MyProjectsResponse>({ teams: [{ team: { id: 7, name: 'Dev' }, projects: [] }], sharedProjects: [] }),
    );
    await open();
    expect(cy('etat-vide')?.textContent).toContain('Aucun projet ne vous est proposé pour l’instant.');
  });

  it('état de chargement tant que la réponse n’est pas arrivée', async () => {
    listMyProjects.mockReturnValue(new Subject<MyProjectsResponse>());
    await open();
    expect(root.querySelector('app-loading-state')).not.toBeNull();
    expect(cy('groupe-projets-communs')).toBeNull();
  });

  it('état d’erreur avec « Réessayer », qui recharge la liste', async () => {
    listMyProjects.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: { status: 500, detail: 'Erreur serveur' } })),
    );
    await open();
    expect(root.querySelector('app-error-state')).not.toBeNull();
    listMyProjects.mockReturnValue(of<MyProjectsResponse>({ teams: [], sharedProjects: [commun] }));
    root.querySelector<HTMLButtonElement>('app-error-state button')?.click();
    await fixture.whenStable();
    expect(listMyProjects).toHaveBeenCalledTimes(2);
    expect(codes('groupe-projets-communs')).toEqual(['mon-projet-COMMUN']);
  });
});
