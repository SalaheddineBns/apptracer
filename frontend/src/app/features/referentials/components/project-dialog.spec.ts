import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import { ProjectResponse, ProjectsService, Role, TeamResponse, TeamsService } from '../../../core/api/generated';
import { ProjectDialog } from './project-dialog';

const existing = (over: Partial<ProjectResponse> = {}): ProjectResponse => ({ id: 2, code: 'PORTAIL', name: 'Portail', active: true, version: 3, ...over });
const httpError = (status: number, body: object) => throwError(() => new HttpErrorResponse({ status, error: body }));
const marion = { id: 5, firstName: 'Marion', lastName: 'Leroy', email: 'marion@example.com', role: Role.Manager, active: true };
const team = (id: number, name: string): TeamResponse => ({ id, name, manager: marion, members: [], memberCount: 0, version: 0 });
const teams = [team(1, 'Dev'), team(2, 'QA')];

describe('ProjectDialog', () => {
  const createProject = vi.fn();
  const updateProject = vi.fn();
  const listProjects = vi.fn();
  const listTeams = vi.fn();
  const add = vi.fn();
  const saved = vi.fn();
  const conflict = vi.fn();
  let fixture: ReturnType<typeof TestBed.createComponent<ProjectDialog>>;

  async function open(project: ProjectResponse | null): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        { provide: ProjectsService, useValue: { createProject, updateProject, listProjects } },
        { provide: TeamsService, useValue: { listTeams } },
        { provide: MessageService, useValue: { add } },
      ],
    });
    fixture = TestBed.createComponent(ProjectDialog);
    fixture.componentInstance.saved.subscribe(saved);
    fixture.componentInstance.conflict.subscribe(conflict);
    fixture.componentRef.setInput('project', project);
    fixture.componentRef.setInput('visible', true);
    await fixture.whenStable();
    await fixture.whenStable();
  }

  const el = (cy: string) => document.body.querySelector<HTMLElement>(`[data-cy="${cy}"]`);
  const input = (cy: string) => el(cy) as HTMLInputElement;
  const error = (cy: string) => el(`erreur-${cy}`);
  async function type(cy: string, value: string): Promise<void> {
    input(cy).value = value;
    input(cy).dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }
  async function submit(): Promise<void> {
    el('btn-enregistrer')?.querySelector('button')?.click();
    await fixture.whenStable();
  }
  async function fillCreate(code = 'PORTAIL_CLIENT'): Promise<void> {
    await type('champ-code-projet', code);
    await type('champ-nom-projet', 'Portail client');
  }

  beforeEach(() => {
    for (const fn of [createProject, updateProject, listProjects, listTeams, add, saved, conflict]) {
      fn.mockReset();
    }
    listProjects.mockReturnValue(of([]));
    listTeams.mockReturnValue(of(teams));
    TestBed.resetTestingModule();
    document.body.querySelectorAll('p-dialog, .p-dialog-mask').forEach((n) => n.remove());
  });

  it('création réussie : envoie code et nom, ferme et signale la création', async () => {
    createProject.mockReturnValue(of(existing({ id: 9, code: 'PORTAIL_CLIENT', name: 'Portail client' })));
    await open(null);
    await fillCreate();
    await submit();
    expect(createProject).toHaveBeenCalledWith({ code: 'PORTAIL_CLIENT', name: 'Portail client' });
    expect(saved).toHaveBeenCalledWith(expect.objectContaining({ mode: 'create' }));
    expect(fixture.componentInstance.visible()).toBe(false);
    expect(listProjects).toHaveBeenCalled();
  });

  it('CA-02-03 : code déjà existant, l’erreur RG-02-01 s’affiche sous le champ code, dialogue ouvert et saisie conservée', async () => {
    createProject.mockReturnValue(
      httpError(409, { status: 409, code: 'RG-02-01', detail: 'Ce code est déjà utilisé.', errors: [{ field: 'code', message: 'Ce code est déjà utilisé.' }] }),
    );
    await open(null);
    await fillCreate('PORTAIL');
    await submit();
    expect(error('champ-code-projet')?.textContent).toContain('déjà utilisé');
    expect(fixture.componentInstance.visible()).toBe(true);
    expect(input('champ-code-projet').value).toBe('PORTAIL');
    expect(input('champ-nom-projet').value).toBe('Portail client');
    expect(add).not.toHaveBeenCalled();
    expect(saved).not.toHaveBeenCalled();
  });

  it('CA-02-04 : le code « réunion client » met le champ en erreur, sans mise en majuscules, et bloque l’enregistrement', async () => {
    await open(null);
    await fillCreate('réunion client');
    await submit();
    expect(createProject).not.toHaveBeenCalled();
    expect(input('champ-code-projet').value).toBe('réunion client');
    expect(error('champ-code-projet')?.textContent).toContain('majuscules, chiffres et _');
    expect(fixture.componentInstance.visible()).toBe(true);
  });

  it.each(['A', 'A'.repeat(31), 'AB-C'])('CA-02-04 : le code %s est refusé par le validateur', async (code) => {
    await open(null);
    await fillCreate(code);
    await submit();
    expect(createProject).not.toHaveBeenCalled();
  });

  it.each(['AB', 'A'.repeat(30), 'PROJ_2'])('RG-02-01 : le code %s est accepté', async (code) => {
    createProject.mockReturnValue(of(existing()));
    await open(null);
    await fillCreate(code);
    await submit();
    expect(createProject).toHaveBeenCalledTimes(1);
  });

  it('refuse un nom vide ou de plus de 100 caractères', async () => {
    await open(null);
    await fillCreate();
    await type('champ-nom-projet', 'x'.repeat(101));
    await submit();
    expect(createProject).not.toHaveBeenCalled();
    expect(error('champ-nom-projet')?.textContent).toContain('100 caractères');
    await type('champ-nom-projet', '');
    await submit();
    expect(error('champ-nom-projet')?.textContent).toContain('obligatoire');
  });

  it('affiche sous le champ une erreur 400 du back sur name', async () => {
    createProject.mockReturnValue(httpError(400, { status: 400, detail: 'Requête invalide', errors: [{ field: 'name', message: 'Refusé par le serveur.' }] }));
    await open(null);
    await fillCreate();
    await submit();
    expect(error('champ-nom-projet')?.textContent).toContain('Refusé par le serveur.');
    expect(add).not.toHaveBeenCalled();
  });

  it('CA-02-05 : en modification, le code est affiché mais non modifiable, avec son aide, et absent de la requête', async () => {
    updateProject.mockReturnValue(of(existing({ name: 'Portail 2', version: 4 })));
    await open(existing());
    expect(input('champ-code-projet').disabled).toBe(true);
    expect(input('champ-code-projet').value).toBe('PORTAIL');
    expect(el('dialogue-projet')?.textContent).toContain('Le code ne peut pas être modifié après la création.');
    await type('champ-nom-projet', 'Portail 2');
    await submit();
    expect(updateProject).toHaveBeenCalledWith(2, { name: 'Portail 2', version: 3 });
    expect(saved).toHaveBeenCalledWith(expect.objectContaining({ mode: 'update' }));
    expect(fixture.componentInstance.visible()).toBe(false);
  });

  it('modification concurrente : 409 sans code, le dialogue se ferme et la liste est rechargée', async () => {
    updateProject.mockReturnValue(httpError(409, { status: 409, type: 'https://apptracer/errors/concurrent-modification', detail: 'Ce projet a été modifié entre-temps.' }));
    await open(existing());
    await submit();
    expect(conflict).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.visible()).toBe(false);
    expect(saved).not.toHaveBeenCalled();
  });

  it('annulation : « Annuler » ferme sans appeler l’API', async () => {
    await open(null);
    await fillCreate();
    el('btn-annuler-dialogue')?.querySelector('button')?.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.visible()).toBe(false);
    expect(createProject).not.toHaveBeenCalled();
  });

  it('pendant l’envoi, le dialogue ne peut être fermé ni par Échap, ni par « Annuler », ni par la croix', async () => {
    createProject.mockReturnValue(new Subject());
    await open(null);
    await fillCreate();
    await submit();
    expect(createProject).toHaveBeenCalledTimes(1);
    expect(el('btn-annuler-dialogue')?.querySelector('button')?.disabled).toBe(true);
    expect(document.body.querySelector('.p-dialog-close-button')).toBeNull();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }));
    el('dialogue-projet')?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }));
    await fixture.whenStable();
    expect(fixture.componentInstance.visible()).toBe(true);
  });

  it('la croix du dialogue a un nom accessible « Fermer »', async () => {
    await open(null);
    expect(document.body.querySelector('.p-dialog-close-button')?.getAttribute('aria-label')).toBe('Fermer');
  });

  it("ne reprend pas le focus à la fin de l'animation si la saisie a déjà commencé", async () => {
    await open(null);
    const dialog = fixture.componentInstance as unknown as { focusFirstField(form: HTMLFormElement): void };
    const form = el('dialogue-projet') as HTMLFormElement;
    input('champ-nom-projet').focus();
    dialog.focusFirstField(form);
    expect(document.activeElement).toBe(input('champ-nom-projet'));
    (document.activeElement as HTMLElement).blur();
    dialog.focusFirstField(form);
    expect(document.activeElement).toBe(input('champ-code-projet'));
  });

  it('l’exemple cité par l’erreur du code est celui de l’aide du même champ', async () => {
    await open(null);
    await type('champ-code-projet', 'minuscules');
    input('champ-code-projet').dispatchEvent(new Event('blur'));
    await fixture.whenStable();
    const example = (text: string | null | undefined) => /ex\. (\w+)\)/.exec(text ?? '')?.[1];
    const help = document.body.querySelector('[data-cy="champ-code-projet"]')?.parentElement?.querySelector('small')?.textContent;
    expect(example(help)).toBe('PORTAIL_CLIENT');
    expect(example(el('erreur-champ-code-projet')?.textContent)).toBe(example(help));
  });

  describe('SFD-10 : équipe du projet', () => {
    const teamControl = () => fixture.componentInstance['form'].controls.teamId;

    it('CA-10-01 : les équipes sont proposées dans l’ordre reçu et l’équipe choisie est envoyée à la création', async () => {
      createProject.mockReturnValue(of(existing({ id: 9, code: 'ALPHA', name: 'Alpha', team: { id: 1, name: 'Dev' } })));
      await open(null);
      expect(listTeams).toHaveBeenCalled();
      expect(fixture.componentInstance['teamOptions']()).toEqual([
        { label: 'Dev', value: 1 },
        { label: 'QA', value: 2 },
      ]);
      await type('champ-code-projet', 'ALPHA');
      await type('champ-nom-projet', 'Alpha');
      teamControl().setValue(1);
      await submit();
      expect(createProject).toHaveBeenCalledWith({ code: 'ALPHA', name: 'Alpha', teamId: 1 });
    });

    it('RG-10-01 : sans équipe choisie, le projet est créé commun (aucune équipe envoyée)', async () => {
      createProject.mockReturnValue(of(existing({ id: 9, code: 'COMMUN', name: 'Commun' })));
      await open(null);
      await type('champ-code-projet', 'COMMUN');
      await type('champ-nom-projet', 'Commun');
      await submit();
      expect(createProject).toHaveBeenCalledWith({ code: 'COMMUN', name: 'Commun' });
    });

    it('RG-10-02 : en modification, l’équipe est reprise ; la vider retire l’équipe (rien n’est envoyé)', async () => {
      updateProject.mockReturnValue(of(existing()));
      await open(existing({ team: { id: 2, name: 'QA' } }));
      expect(teamControl().value).toBe(2);
      teamControl().setValue(null);
      await submit();
      expect(updateProject).toHaveBeenCalledWith(2, { name: 'Portail', version: 3 });
    });

    it('RG-10-01 : une équipe refusée par l’API s’affiche sous le champ équipe, dialogue ouvert', async () => {
      createProject.mockReturnValue(
        httpError(409, { status: 409, code: 'RG-10-01', detail: 'Équipe inconnue.', errors: [{ field: 'teamId', message: 'Choisissez une équipe existante.' }] }),
      );
      await open(null);
      await type('champ-code-projet', 'ALPHA');
      await type('champ-nom-projet', 'Alpha');
      teamControl().setValue(99);
      await submit();
      expect(error('champ-equipe-projet')?.textContent).toContain('équipe existante');
      expect(fixture.componentInstance.visible()).toBe(true);
    });

    it('chargement des équipes en échec : message et bouton Réessayer qui relance le chargement', async () => {
      listTeams.mockReturnValue(httpError(500, { status: 500, detail: 'Erreur' }));
      await open(null);
      expect(el('erreur-chargement-equipes')).not.toBeNull();
      listTeams.mockReturnValue(of(teams));
      el('btn-recharger-equipes')?.querySelector('button')?.click();
      await fixture.whenStable();
      expect(el('erreur-chargement-equipes')).toBeNull();
    });
  });
});
