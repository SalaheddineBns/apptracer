import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import { JobTitleResponse, JobTitlesService } from '../../../core/api/generated';
import { JobTitleDialog } from './job-title-dialog';

const existing = (over: Partial<JobTitleResponse> = {}): JobTitleResponse => ({
  id: 2,
  code: 'TECH_LEAD',
  label: 'Tech Lead',
  active: true,
  version: 3,
  ...over,
});
const httpError = (status: number, body: object) => throwError(() => new HttpErrorResponse({ status, error: body }));

describe('JobTitleDialog', () => {
  const createJobTitle = vi.fn();
  const updateJobTitle = vi.fn();
  const listJobTitles = vi.fn();
  const add = vi.fn();
  const saved = vi.fn();
  const conflict = vi.fn();
  let fixture: ReturnType<typeof TestBed.createComponent<JobTitleDialog>>;

  async function open(jobTitle: JobTitleResponse | null): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        { provide: JobTitlesService, useValue: { createJobTitle, updateJobTitle, listJobTitles } },
        { provide: MessageService, useValue: { add } },
      ],
    });
    fixture = TestBed.createComponent(JobTitleDialog);
    fixture.componentInstance.saved.subscribe(saved);
    fixture.componentInstance.conflict.subscribe(conflict);
    fixture.componentRef.setInput('jobTitle', jobTitle);
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
  async function fillCreate(code = 'UX_DESIGNER'): Promise<void> {
    await type('champ-code-fonction', code);
    await type('champ-libelle-fonction', 'UX Designer');
  }

  beforeEach(() => {
    for (const fn of [createJobTitle, updateJobTitle, listJobTitles, add, saved, conflict]) {
      fn.mockReset();
    }
    listJobTitles.mockReturnValue(of([]));
    TestBed.resetTestingModule();
    document.body.querySelectorAll('p-dialog, .p-dialog-mask').forEach((n) => n.remove());
  });

  it('CA-09-02 : création réussie, envoie code et libellé, ferme, signale la création et recharge la liste', async () => {
    createJobTitle.mockReturnValue(of(existing({ id: 9, code: 'UX_DESIGNER', label: 'UX Designer' })));
    await open(null);
    await fillCreate();
    await submit();
    expect(createJobTitle).toHaveBeenCalledWith({ code: 'UX_DESIGNER', label: 'UX Designer' });
    expect(saved).toHaveBeenCalledWith(expect.objectContaining({ mode: 'create' }));
    expect(fixture.componentInstance.visible()).toBe(false);
    expect(listJobTitles).toHaveBeenCalled();
  });

  it('CA-09-03 : code déjà existant (409), l’erreur s’affiche sous le champ code, dialogue ouvert et saisie conservée', async () => {
    createJobTitle.mockReturnValue(
      httpError(409, { status: 409, code: 'RG-09-05', detail: 'Ce code est déjà utilisé.', errors: [{ field: 'code', message: 'Ce code est déjà utilisé.' }] }),
    );
    await open(null);
    await fillCreate('TECH_LEAD');
    await submit();
    expect(error('champ-code-fonction')?.textContent).toContain('déjà utilisé');
    expect(fixture.componentInstance.visible()).toBe(true);
    expect(input('champ-code-fonction').value).toBe('TECH_LEAD');
    expect(input('champ-libelle-fonction').value).toBe('UX Designer');
    expect(add).not.toHaveBeenCalled();
    expect(saved).not.toHaveBeenCalled();
  });

  it('CA-09-03 : le code « tech lead » met le champ en erreur, sans mise en majuscules, et bloque l’enregistrement', async () => {
    await open(null);
    await fillCreate('tech lead');
    await submit();
    expect(createJobTitle).not.toHaveBeenCalled();
    expect(input('champ-code-fonction').value).toBe('tech lead');
    expect(error('champ-code-fonction')?.textContent).toContain('majuscules, chiffres et _');
    expect(fixture.componentInstance.visible()).toBe(true);
  });

  it.each(['A', 'A'.repeat(31), 'AB-C'])('CA-09-03 : le code %s est refusé par le validateur', async (code) => {
    await open(null);
    await fillCreate(code);
    await submit();
    expect(createJobTitle).not.toHaveBeenCalled();
  });

  it.each(['AB', 'A'.repeat(30), 'DEVOPS_2'])('RG-09-05 : le code %s est accepté', async (code) => {
    createJobTitle.mockReturnValue(of(existing()));
    await open(null);
    await fillCreate(code);
    await submit();
    expect(createJobTitle).toHaveBeenCalledTimes(1);
  });

  it('RG-09-05 : refuse un libellé vide ou de plus de 100 caractères', async () => {
    await open(null);
    await fillCreate();
    await type('champ-libelle-fonction', 'x'.repeat(101));
    await submit();
    expect(createJobTitle).not.toHaveBeenCalled();
    expect(error('champ-libelle-fonction')?.textContent).toContain('100 caractères');
    await type('champ-libelle-fonction', '');
    await submit();
    expect(error('champ-libelle-fonction')?.textContent).toContain('obligatoire');
  });

  it('affiche sous le champ une erreur 400 du back sur label', async () => {
    createJobTitle.mockReturnValue(httpError(400, { status: 400, detail: 'Requête invalide', errors: [{ field: 'label', message: 'Refusé par le serveur.' }] }));
    await open(null);
    await fillCreate();
    await submit();
    expect(error('champ-libelle-fonction')?.textContent).toContain('Refusé par le serveur.');
    expect(add).not.toHaveBeenCalled();
  });

  it('RG-09-05 : en modification, le code est affiché mais non modifiable, avec son aide, et absent de la requête', async () => {
    updateJobTitle.mockReturnValue(of(existing({ label: 'Tech Leader', version: 4 })));
    await open(existing());
    expect(input('champ-code-fonction').disabled).toBe(true);
    expect(input('champ-code-fonction').value).toBe('TECH_LEAD');
    expect(el('dialogue-fonction')?.textContent).toContain('Le code ne peut pas être modifié après la création.');
    await type('champ-libelle-fonction', 'Tech Leader');
    await submit();
    expect(updateJobTitle).toHaveBeenCalledWith(2, { label: 'Tech Leader', version: 3 });
    expect(saved).toHaveBeenCalledWith(expect.objectContaining({ mode: 'update' }));
    expect(fixture.componentInstance.visible()).toBe(false);
  });

  it('modification concurrente : 409 sans code, le dialogue se ferme et la liste est rechargée', async () => {
    updateJobTitle.mockReturnValue(httpError(409, { status: 409, type: 'https://apptracer/errors/concurrent-modification', detail: 'Cette fonction a été modifiée entre-temps.' }));
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
    expect(createJobTitle).not.toHaveBeenCalled();
  });

  it('pendant l’envoi, « Annuler » est désactivé et la croix absente, un second envoi est ignoré', async () => {
    createJobTitle.mockReturnValue(new Subject());
    await open(null);
    await fillCreate();
    await submit();
    await submit();
    expect(createJobTitle).toHaveBeenCalledTimes(1);
    expect(el('btn-annuler-dialogue')?.querySelector('button')?.disabled).toBe(true);
    expect(document.body.querySelector('.p-dialog-close-button')).toBeNull();
  });
});
