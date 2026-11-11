import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import { ActivityTypeResponse, ActivityTypesService, TagColor } from '../../../core/api/generated';
import { ActivityTypesPage } from './activity-types';

const type = (id: number, over: Partial<ActivityTypeResponse> = {}): ActivityTypeResponse => ({
  id,
  code: `TYPE_${id}`,
  label: `Type ${id}`,
  projectRequired: false,
  color: TagColor.Info,
  active: true,
  version: 0,
  ...over,
});

describe('ActivityTypesPage', () => {
  const listActivityTypes = vi.fn();
  const deactivateActivityType = vi.fn();
  const reactivateActivityType = vi.fn();
  const confirm = vi.fn();
  const add = vi.fn();
  let root: HTMLElement;
  let fixtureRef: ReturnType<typeof TestBed.createComponent<ActivityTypesPage>>;
  let whenStable: () => Promise<unknown>;

  async function open(): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        { provide: ActivityTypesService, useValue: { listActivityTypes, deactivateActivityType, reactivateActivityType } },
        { provide: ConfirmationService, useValue: { confirm } },
        { provide: MessageService, useValue: { add } },
      ],
    });
    const fixture = TestBed.createComponent(ActivityTypesPage);
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
    // jsdom has no ResizeObserver, used by the frozen column (pFrozenColumn).
    vi.stubGlobal('ResizeObserver', class { observe = vi.fn(); unobserve = vi.fn(); disconnect = vi.fn(); });
    for (const fn of [listActivityTypes, deactivateActivityType, reactivateActivityType, confirm, add]) {
      fn.mockReset();
    }
    TestBed.resetTestingModule();
  });

  it('affiche l’état de chargement tant que la liste n’est pas arrivée', async () => {
    listActivityTypes.mockReturnValue(new Subject());
    await open();
    expect(cy('etat-chargement')).not.toBeNull();
    expect(cy('table-types-activite')).toBeNull();
  });

  it('affiche l’état vide quand il n’y a aucun type', async () => {
    listActivityTypes.mockReturnValue(of([]));
    await open();
    expect(cy('etat-vide')).not.toBeNull();
  });

  it('affiche l’état d’erreur avec « Réessayer », qui recharge la liste', async () => {
    listActivityTypes.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })));
    await open();
    expect(cy('etat-erreur')?.textContent).toContain('Panne serveur');
    listActivityTypes.mockReturnValue(of([type(1)]));
    await click('btn-reessayer');
    expect(cy('table-types-activite')).not.toBeNull();
    expect(cy('etat-erreur')).toBeNull();
  });

  it('RG-02-04 : état nominal, une seule requête, ordre du back, tag coloré avec libellé, projet requis et statut en toutes lettres', async () => {
    listActivityTypes.mockReturnValue(
      of([type(2, { label: 'Zèbre', projectRequired: true, color: TagColor.Warn }), type(1, { label: 'Alpha', active: false })]),
    );
    await open();
    expect(listActivityTypes).toHaveBeenCalledTimes(1);
    const rows = Array.from(root.querySelectorAll('[data-cy^="ligne-type-"]')).map((r) => r.getAttribute('data-cy'));
    expect(rows).toEqual(['ligne-type-2', 'ligne-type-1']);
    const row = cy('ligne-type-2') as HTMLElement;
    expect(row.textContent).toContain('TYPE_2');
    expect(row.querySelector('[data-cy="activity-type-tag"]')?.textContent).toContain('Zèbre');
    expect(row.querySelector('.p-tag-warn')).not.toBeNull();
    expect(cy('projet-requis-type-2')?.textContent).toContain('Oui');
    expect(cy('projet-requis-type-1')?.textContent).toContain('Non');
    expect(cy('statut-type-1')?.textContent).toContain('Inactif');
    expect(cy('statut-type-2')?.textContent).toContain('Actif');
  });

  it('RG-02-05 : la désactivation demande confirmation, annulée elle n’appelle pas l’API', async () => {
    listActivityTypes.mockReturnValue(of([type(1, { label: 'Réunion' })]));
    await open();
    await click('btn-desactiver-type-1');
    expect(confirm).toHaveBeenCalledTimes(1);
    const options = confirm.mock.calls[0][0];
    // Escape must cancel: the confirmation must never disable `closable`.
    expect(options.closable).not.toBe(false);
    expect(options.message).toContain('Réunion');
    expect(options.message).toContain('plus proposé pour une nouvelle saisie');
    expect(options.message).toContain('saisies existantes sont conservées');
    expect(deactivateActivityType).not.toHaveBeenCalled();
    expect(cy('statut-type-1')?.textContent).toContain('Actif');
  });

  it('RG-02-05 : désactivation confirmée met à jour la ligne et affiche un toast de 3 s', async () => {
    listActivityTypes.mockReturnValue(of([type(1)]));
    deactivateActivityType.mockReturnValue(of(type(1, { active: false })));
    await open();
    await click('btn-desactiver-type-1');
    confirm.mock.calls[0][0].accept();
    await whenStable();
    expect(deactivateActivityType).toHaveBeenCalledWith(1);
    expect(cy('statut-type-1')?.textContent).toContain('Inactif');
    expect(cy('btn-desactiver-type-1')).toBeNull();
    expect(cy('btn-reactiver-type-1')).not.toBeNull();
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', life: 3000 }));
    expect(listActivityTypes).toHaveBeenCalledTimes(1);
  });

  it('RG-02-05 : la réactivation est directe, sans confirmation', async () => {
    listActivityTypes.mockReturnValue(of([type(1, { active: false })]));
    reactivateActivityType.mockReturnValue(of(type(1)));
    await open();
    expect(cy('btn-desactiver-type-1')).toBeNull();
    await click('btn-reactiver-type-1');
    expect(confirm).not.toHaveBeenCalled();
    expect(reactivateActivityType).toHaveBeenCalledWith(1);
    expect(cy('statut-type-1')?.textContent).toContain('Actif');
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', life: 3000 }));
  });

  it('« Nouveau type » (en-tête et état vide) et « Modifier » ouvrent le dialogue E2', async () => {
    listActivityTypes.mockReturnValue(of([type(1, { label: 'Réunion' })]));
    await open();
    expect(cy('btn-modifier-type-1')?.querySelector('button')?.getAttribute('aria-label')).toBe('Modifier le type Réunion');
    await click('btn-nouveau-type');
    const page = fixtureRef.componentInstance as unknown as { dialogVisible(): boolean; editing(): unknown };
    expect(page.dialogVisible()).toBe(true);
    expect(page.editing()).toBeNull();
    await click('btn-modifier-type-1');
    expect(page.editing()).toEqual(expect.objectContaining({ id: 1 }));
  });

  it('l’état vide propose « Nouveau type »', async () => {
    listActivityTypes.mockReturnValue(of([]));
    await open();
    expect(cy('btn-nouveau-type-vide')).not.toBeNull();
  });

  it('le toast de création a le titre « Type créé », celui d’une modification « Type mis à jour »', async () => {
    listActivityTypes.mockReturnValue(of([]));
    await open();
    const page = fixtureRef.componentInstance as unknown as { onSaved(e: { mode: 'create' | 'update'; type: ActivityTypeResponse }): void };
    page.onSaved({ mode: 'create', type: type(1) });
    page.onSaved({ mode: 'update', type: type(1) });
    expect(add.mock.calls.map((c) => c[0].summary)).toEqual(['Type créé', 'Type mis à jour']);
  });

  it('les actions par ligne nomment le type dans leur aria-label', async () => {
    listActivityTypes.mockReturnValue(of([type(1, { label: 'Réunion' })]));
    await open();
    expect(cy('btn-desactiver-type-1')?.querySelector('button')?.getAttribute('aria-label')).toBe('Désactiver le type Réunion');
  });

  it('F1 : trois appuis rapides sur « Réactiver » n’envoient qu’une requête, bouton désactivé jusqu’à la réponse', async () => {
    listActivityTypes.mockReturnValue(of([type(1, { active: false })]));
    const response = new Subject<ActivityTypeResponse>();
    reactivateActivityType.mockReturnValue(response);
    await open();
    const button = cy('btn-reactiver-type-1')?.querySelector('button') as HTMLButtonElement;
    button.click();
    button.click();
    button.click();
    await whenStable();
    expect(reactivateActivityType).toHaveBeenCalledTimes(1);
    expect(cy('btn-reactiver-type-1')?.querySelector('button')?.disabled).toBe(true);
    response.next(type(1));
    response.complete();
    await whenStable();
    expect(add).toHaveBeenCalledTimes(1);
    expect(cy('btn-desactiver-type-1')?.querySelector('button')?.disabled).toBe(false);
  });

  it('F1 : une désactivation confirmée deux fois n’envoie qu’une requête, bouton désactivé jusqu’à la réponse', async () => {
    listActivityTypes.mockReturnValue(of([type(1, { label: '<u>Souligné</u>' })]));
    const response = new Subject<ActivityTypeResponse>();
    deactivateActivityType.mockReturnValue(response);
    await open();
    await click('btn-desactiver-type-1');
    const { accept } = confirm.mock.calls[0][0];
    accept();
    accept();
    await whenStable();
    expect(deactivateActivityType).toHaveBeenCalledTimes(1);
    expect(cy('btn-desactiver-type-1')?.querySelector('button')?.disabled).toBe(true);
    response.next(type(1, { active: false }));
    response.complete();
    await whenStable();
    expect(add).toHaveBeenCalledTimes(1);
  });
});
