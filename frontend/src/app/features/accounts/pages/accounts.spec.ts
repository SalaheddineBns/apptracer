import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import { Role, UserResponse, UsersService } from '../../../core/api/generated';
import { AuthStore } from '../../../core/auth/auth.store';
import { AccountsStore } from '../data/accounts.store';
import { AccountsPage } from './accounts';

const account = (id: number, over: Partial<UserResponse> = {}): UserResponse => ({
  id,
  email: `u${id}@x.fr`,
  firstName: `Prénom${id}`,
  lastName: `Nom${id}`,
  role: Role.User,
  active: true,
  version: 0,
  ...over,
});
const pageOf = (content: UserResponse[]) => of({ content, page: 0, size: 20, totalElements: content.length });

describe('AccountsPage', () => {
  const listUsers = vi.fn();
  const deactivateUser = vi.fn();
  const reactivateUser = vi.fn();
  const confirm = vi.fn();
  const add = vi.fn();
  let root: HTMLElement;
  let whenStable: () => Promise<unknown>;
  let page: AccountsPage;

  async function open(): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        { provide: UsersService, useValue: { listUsers, deactivateUser, reactivateUser } },
        { provide: AuthStore, useValue: { user: signal({ id: 1, email: 'a@x.fr', firstName: 'Ada', lastName: 'Admin', role: Role.Admin }) } },
        { provide: ConfirmationService, useValue: { confirm } },
        { provide: MessageService, useValue: { add } },
      ],
    });
    const fixture = TestBed.createComponent(AccountsPage);
    page = fixture.componentInstance;
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
    for (const fn of [listUsers, deactivateUser, reactivateUser, confirm, add]) {
      fn.mockReset();
    }
    TestBed.resetTestingModule();
  });

  it('affiche l’état de chargement tant que la liste n’est pas arrivée', async () => {
    listUsers.mockReturnValue(new Subject());
    await open();
    expect(cy('etat-chargement')).not.toBeNull();
    expect(cy('table-comptes')).toBeNull();
  });

  it('affiche l’état vide quand il n’y a aucun compte', async () => {
    listUsers.mockReturnValue(pageOf([]));
    await open();
    expect(cy('etat-vide')).not.toBeNull();
  });

  it('affiche l’état d’erreur avec « Réessayer », qui recharge la liste', async () => {
    listUsers.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })));
    await open();
    expect(cy('etat-erreur')?.textContent).toContain('Panne serveur');
    listUsers.mockReturnValue(pageOf([account(2)]));
    await click('btn-reessayer');
    expect(cy('table-comptes')).not.toBeNull();
    expect(cy('etat-erreur')).toBeNull();
  });

  it('état nominal : nom, e-mail, rôle et statut de chaque compte, tri par défaut sur le nom', async () => {
    listUsers.mockReturnValue(pageOf([account(2, { role: Role.Manager }), account(3, { active: false })]));
    await open();
    expect(listUsers).toHaveBeenCalledWith(0, 20, ['lastName,asc'], 'body', false, expect.anything());
    const row = cy('ligne-compte-2') as HTMLElement;
    expect(row.textContent).toContain('Prénom2 Nom2');
    expect(row.textContent).toContain('u2@x.fr');
    expect(row.textContent).toContain('Manager');
    expect(cy('statut-compte-3')?.textContent).toContain('Inactif');
  });

  it('CA-03-12 : l’action « Désactiver » est absente sur la propre ligne de l’ADMIN, et un 409 de l’API laisse la liste inchangée', async () => {
    listUsers.mockReturnValue(pageOf([account(1, { role: Role.Admin }), account(2)]));
    await open();
    expect(cy('btn-desactiver-1')).toBeNull();
    expect(cy('indicateur-vous')).not.toBeNull();
    expect(cy('btn-desactiver-2')).not.toBeNull();

    deactivateUser.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'RG-03-13', detail: 'Vous ne pouvez pas désactiver votre propre compte.' } })));
    let failed = false;
    TestBed.inject(AccountsStore).deactivate(1).subscribe({ error: () => (failed = true) });
    expect(failed).toBe(true);
    expect(TestBed.inject(AccountsStore).users().find((u) => u.id === 1)?.active).toBe(true);
  });

  it('le toast de création a le titre « Compte créé », celui d’une modification « Compte mis à jour »', async () => {
    listUsers.mockReturnValue(pageOf([]));
    await open();
    const p = page as unknown as { onSaved(e: { mode: 'create' | 'update'; user: UserResponse }): void };
    p.onSaved({ mode: 'create', user: account(2) });
    p.onSaved({ mode: 'update', user: account(2) });
    expect(add.mock.calls.map((c) => c[0].summary)).toEqual(['Compte créé', 'Compte mis à jour']);
  });

  it('désactivation confirmée : appelle l’API, met à jour la ligne et affiche un toast', async () => {
    listUsers.mockReturnValue(pageOf([account(2)]));
    deactivateUser.mockReturnValue(of(account(2, { active: false })));
    await open();
    await click('btn-desactiver-2');
    expect(confirm).toHaveBeenCalledTimes(1);
    // Échap doit annuler : la confirmation ne doit jamais désactiver `closable`.
    expect(confirm.mock.calls[0][0].closable).not.toBe(false);
    expect(confirm.mock.calls[0][0].message).toContain('Prénom2 Nom2');
    expect(deactivateUser).not.toHaveBeenCalled();
    confirm.mock.calls[0][0].accept();
    await whenStable();
    expect(deactivateUser).toHaveBeenCalledWith(2);
    expect(cy('statut-compte-2')?.textContent).toContain('Inactif');
    expect(cy('btn-reactiver-2')).not.toBeNull();
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('CA-05-07 : le refus 409 RG-05-10 laisse la ligne « Actif », sans toast de succès, et le bouton « Désactiver » redevient disponible', async () => {
    listUsers.mockReturnValue(pageOf([account(2, { role: Role.Manager })]));
    deactivateUser.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'RG-05-10', detail: 'Ce compte est manager de l’équipe « Produit ».' } })),
    );
    await open();
    await click('btn-desactiver-2');
    confirm.mock.calls[0][0].accept();
    await whenStable();
    expect(deactivateUser).toHaveBeenCalledWith(2);
    expect(cy('statut-compte-2')?.textContent).toContain('Actif');
    expect(cy('btn-desactiver-2')).not.toBeNull();
    expect((cy('btn-desactiver-2')?.querySelector('button') as HTMLButtonElement).disabled).toBe(false);
    expect(cy('btn-reactiver-2')).toBeNull();
    expect(add).not.toHaveBeenCalled();
  });

  it('désactivation annulée : aucun appel à l’API', async () => {
    listUsers.mockReturnValue(pageOf([account(2)]));
    await open();
    await click('btn-desactiver-2');
    expect(confirm).toHaveBeenCalledTimes(1);
    expect(deactivateUser).not.toHaveBeenCalled();
    expect(cy('statut-compte-2')?.textContent).toContain('Actif');
  });

  it('réactivation : directe, sans confirmation', async () => {
    listUsers.mockReturnValue(pageOf([account(3, { active: false })]));
    reactivateUser.mockReturnValue(of(account(3)));
    await open();
    await click('btn-reactiver-3');
    expect(confirm).not.toHaveBeenCalled();
    expect(reactivateUser).toHaveBeenCalledWith(3);
    expect(cy('statut-compte-3')?.textContent).toContain('Actif');
  });

  it('pagination et tri côté serveur, avec des champs de tri acceptés par le back uniquement', async () => {
    listUsers.mockReturnValue(pageOf([account(2)]));
    await open();
    listUsers.mockClear();
    page['onLazyLoad']({ first: 20, rows: 10, sortField: 'role', sortOrder: -1 });
    expect(listUsers).toHaveBeenLastCalledWith(2, 10, ['role,desc'], 'body', false, expect.anything());
    page['onLazyLoad']({ first: 0, rows: 10, sortField: 'passwordHash', sortOrder: 1 });
    expect(listUsers).toHaveBeenLastCalledWith(0, 10, ['role,asc'], 'body', false, expect.anything());
  });

  it('« Nouveau compte » et « Modifier » ouvrent le dialogue en création puis en modification', async () => {
    listUsers.mockReturnValue(pageOf([account(2)]));
    await open();
    await click('btn-nouveau-compte');
    expect(page['dialogVisible']()).toBe(true);
    expect(page['editing']()).toBeNull();
    page['dialogVisible'].set(false);
    await click('btn-modifier-2');
    expect(page['dialogVisible']()).toBe(true);
    expect(page['editing']()?.id).toBe(2);
    expect(cy('btn-modifier-2')?.querySelector('button')?.getAttribute('aria-label')).toBe('Modifier le compte de Prénom2 Nom2');
  });

  it('une seule requête GET /users au chargement, et une seule par tri ou changement de page', async () => {
    listUsers.mockReturnValue(pageOf([account(2), account(3)]));
    await open();
    expect(listUsers).toHaveBeenCalledTimes(1);
    root.querySelectorAll<HTMLElement>('th')[1]?.click();
    await whenStable();
    expect(listUsers).toHaveBeenCalledTimes(2);
    // Un événement identique à l'état courant (réémission de p-table) ne recharge pas.
    page['onLazyLoad']({ first: 0, rows: 20, sortField: 'email', sortOrder: 1 });
    expect(listUsers).toHaveBeenCalledTimes(2);
  });

  it('l’état vide propose l’action « Nouveau compte »', async () => {
    listUsers.mockReturnValue(pageOf([]));
    await open();
    await click('btn-nouveau-compte-vide');
    expect(page['dialogVisible']()).toBe(true);
  });

  it('la confirmation de désactivation n’a pas de croix de fermeture', async () => {
    listUsers.mockReturnValue(pageOf([account(2)]));
    await open();
    await click('btn-desactiver-2');
  });

  it('F1 : trois appuis rapides sur « Réactiver » n’envoient qu’une requête, bouton désactivé jusqu’à la réponse', async () => {
    listUsers.mockReturnValue(pageOf([account(2, { active: false })]));
    const response = new Subject<UserResponse>();
    reactivateUser.mockReturnValue(response);
    await open();
    const button = cy('btn-reactiver-2')?.querySelector('button') as HTMLButtonElement;
    button.click();
    button.click();
    button.click();
    await whenStable();
    expect(reactivateUser).toHaveBeenCalledTimes(1);
    expect(cy('btn-reactiver-2')?.querySelector('button')?.disabled).toBe(true);
    response.next(account(2));
    response.complete();
    await whenStable();
    expect(add).toHaveBeenCalledTimes(1);
    expect(cy('btn-desactiver-2')?.querySelector('button')?.disabled).toBe(false);
  });

  it('F1 : une désactivation confirmée deux fois n’envoie qu’une requête, bouton désactivé jusqu’à la réponse', async () => {
    listUsers.mockReturnValue(pageOf([account(2, { lastName: '<u>Souligné</u>' })]));
    const response = new Subject<UserResponse>();
    deactivateUser.mockReturnValue(response);
    await open();
    await click('btn-desactiver-2');
    const { accept } = confirm.mock.calls[0][0];
    accept();
    accept();
    await whenStable();
    expect(deactivateUser).toHaveBeenCalledTimes(1);
    expect(cy('btn-desactiver-2')?.querySelector('button')?.disabled).toBe(true);
    response.next(account(2, { active: false }));
    response.complete();
    await whenStable();
    expect(add).toHaveBeenCalledTimes(1);
  });
});
