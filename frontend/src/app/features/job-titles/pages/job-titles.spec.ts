import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import { JobTitleResponse, JobTitlesService } from '../../../core/api/generated';
import { JobTitlesPage } from './job-titles';

const jobTitle = (id: number, over: Partial<JobTitleResponse> = {}): JobTitleResponse => ({
  id,
  code: `FONCTION_${id}`,
  label: `Fonction ${id}`,
  active: true,
  version: 0,
  ...over,
});

describe('JobTitlesPage', () => {
  const listJobTitles = vi.fn();
  const deactivateJobTitle = vi.fn();
  const reactivateJobTitle = vi.fn();
  const confirm = vi.fn();
  const add = vi.fn();
  let root: HTMLElement;
  let fixtureRef: ReturnType<typeof TestBed.createComponent<JobTitlesPage>>;
  let whenStable: () => Promise<unknown>;

  async function open(): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        { provide: JobTitlesService, useValue: { listJobTitles, deactivateJobTitle, reactivateJobTitle } },
        { provide: ConfirmationService, useValue: { confirm } },
        { provide: MessageService, useValue: { add } },
      ],
    });
    const fixture = TestBed.createComponent(JobTitlesPage);
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
    for (const fn of [listJobTitles, deactivateJobTitle, reactivateJobTitle, confirm, add]) {
      fn.mockReset();
    }
    TestBed.resetTestingModule();
  });

  it('affiche l’état de chargement tant que la liste n’est pas arrivée', async () => {
    listJobTitles.mockReturnValue(new Subject());
    await open();
    expect(cy('etat-chargement')).not.toBeNull();
    expect(cy('table-fonctions')).toBeNull();
  });

  it('affiche l’état vide, avec « Nouvelle fonction »', async () => {
    listJobTitles.mockReturnValue(of([]));
    await open();
    expect(cy('etat-vide')).not.toBeNull();
    expect(cy('btn-nouvelle-fonction-vide')).not.toBeNull();
  });

  it('affiche l’état d’erreur avec « Réessayer », qui recharge la liste', async () => {
    listJobTitles.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })));
    await open();
    expect(cy('etat-erreur')?.textContent).toContain('Panne serveur');
    listJobTitles.mockReturnValue(of([jobTitle(1)]));
    await click('btn-reessayer');
    expect(cy('table-fonctions')).not.toBeNull();
    expect(cy('etat-erreur')).toBeNull();
  });

  it('CA-09-01 : la liste garde l’ordre de l’API (libellé alphabétique), code, libellé et statut en toutes lettres', async () => {
    const labels = ['Data Analyst', 'Developer', 'Product Owner', 'QA', 'Scrum Master', 'Tech Lead'];
    listJobTitles.mockReturnValue(of(labels.map((label, i) => jobTitle(i + 1, { label, code: label.toUpperCase().replace(' ', '_') }))));
    await open();
    expect(listJobTitles).toHaveBeenCalledTimes(1);
    const rows = Array.from(root.querySelectorAll('[data-cy^="ligne-fonction-"]'));
    expect(rows.map((r) => r.querySelectorAll('td')[1].textContent?.trim())).toEqual(labels);
    expect(cy('ligne-fonction-6')?.textContent).toContain('TECH_LEAD');
    for (let id = 1; id <= 6; id++) {
      expect(cy(`statut-fonction-${id}`)?.textContent).toContain('Active');
    }
  });

  it('CA-09-10 : en français, le libellé « Developer » est affiché tel quel (donnée, RG-09-10)', async () => {
    listJobTitles.mockReturnValue(of([jobTitle(2, { label: 'Developer', code: 'DEVELOPER' })]));
    await open();
    expect(cy('ligne-fonction-2')?.textContent).toContain('Developer');
    expect(root.querySelector('h1')?.textContent).toContain('Fonctions');
  });

  it('CA-09-07 : un statut inactif est affiché en toutes lettres', async () => {
    listJobTitles.mockReturnValue(of([jobTitle(1, { active: false })]));
    await open();
    expect(cy('statut-fonction-1')?.textContent).toContain('Inactive');
  });

  it('CA-09-07 : la désactivation demande confirmation, annulée elle n’appelle pas l’API', async () => {
    listJobTitles.mockReturnValue(of([jobTitle(1, { label: 'QA' })]));
    await open();
    await click('btn-desactiver-fonction-1');
    expect(confirm).toHaveBeenCalledTimes(1);
    const options = confirm.mock.calls[0][0];
    expect(options.closable).not.toBe(false);
    expect(options.message).toContain('QA');
    expect(options.message).toContain('Inactive');
    expect(deactivateJobTitle).not.toHaveBeenCalled();
    expect(cy('statut-fonction-1')?.textContent).toContain('Active');
  });

  it('CA-09-07 : désactivation confirmée met à jour la ligne et affiche un toast de 3 s', async () => {
    listJobTitles.mockReturnValue(of([jobTitle(1)]));
    deactivateJobTitle.mockReturnValue(of(jobTitle(1, { active: false })));
    await open();
    await click('btn-desactiver-fonction-1');
    confirm.mock.calls[0][0].accept();
    await whenStable();
    expect(deactivateJobTitle).toHaveBeenCalledWith(1);
    expect(cy('statut-fonction-1')?.textContent).toContain('Inactive');
    expect(cy('btn-desactiver-fonction-1')).toBeNull();
    expect(cy('btn-reactiver-fonction-1')).not.toBeNull();
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', life: 3000 }));
    expect(listJobTitles).toHaveBeenCalledTimes(1);
  });

  it('CA-09-07 : la réactivation est directe, sans confirmation', async () => {
    listJobTitles.mockReturnValue(of([jobTitle(1, { active: false })]));
    reactivateJobTitle.mockReturnValue(of(jobTitle(1)));
    await open();
    expect(cy('btn-desactiver-fonction-1')).toBeNull();
    await click('btn-reactiver-fonction-1');
    expect(confirm).not.toHaveBeenCalled();
    expect(reactivateJobTitle).toHaveBeenCalledWith(1);
    expect(cy('statut-fonction-1')?.textContent).toContain('Active');
    expect(cy('btn-desactiver-fonction-1')).not.toBeNull();
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', life: 3000 }));
  });

  it('« Nouvelle fonction » et « Modifier » ouvrent le dialogue E2, les actions nomment la fonction', async () => {
    listJobTitles.mockReturnValue(of([jobTitle(1, { label: 'QA' })]));
    await open();
    expect(cy('btn-modifier-fonction-1')?.querySelector('button')?.getAttribute('aria-label')).toBe('Modifier la fonction QA');
    expect(cy('btn-desactiver-fonction-1')?.querySelector('button')?.getAttribute('aria-label')).toBe('Désactiver la fonction QA');
    await click('btn-nouvelle-fonction');
    const page = fixtureRef.componentInstance as unknown as { dialogVisible(): boolean; editing(): unknown };
    expect(page.dialogVisible()).toBe(true);
    expect(page.editing()).toBeNull();
    await click('btn-modifier-fonction-1');
    expect(page.editing()).toEqual(expect.objectContaining({ id: 1 }));
  });

  it('CA-09-02 : le toast de création a le titre « Fonction créée », celui d’une modification « Fonction mise à jour »', async () => {
    listJobTitles.mockReturnValue(of([]));
    await open();
    const page = fixtureRef.componentInstance as unknown as { onSaved(e: { mode: 'create' | 'update'; jobTitle: JobTitleResponse }): void };
    page.onSaved({ mode: 'create', jobTitle: jobTitle(1) });
    page.onSaved({ mode: 'update', jobTitle: jobTitle(1) });
    expect(add.mock.calls.map((c) => c[0].summary)).toEqual(['Fonction créée', 'Fonction mise à jour']);
  });

  it('trois appuis rapides sur « Réactiver » n’envoient qu’une requête, bouton désactivé jusqu’à la réponse', async () => {
    listJobTitles.mockReturnValue(of([jobTitle(1, { active: false })]));
    const response = new Subject<JobTitleResponse>();
    reactivateJobTitle.mockReturnValue(response);
    await open();
    const button = cy('btn-reactiver-fonction-1')?.querySelector('button') as HTMLButtonElement;
    button.click();
    button.click();
    button.click();
    await whenStable();
    expect(reactivateJobTitle).toHaveBeenCalledTimes(1);
    expect(cy('btn-reactiver-fonction-1')?.querySelector('button')?.disabled).toBe(true);
    response.next(jobTitle(1));
    response.complete();
    await whenStable();
    expect(add).toHaveBeenCalledTimes(1);
  });
});
