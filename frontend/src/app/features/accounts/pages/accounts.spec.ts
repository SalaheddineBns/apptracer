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

  it('désactivation confirmée : appelle l’API, met à jour la ligne et affiche un toast', async () => {
    listUsers.mockReturnValue(pageOf([account(2)]));
    deactivateUser.mockReturnValue(of(account(2, { active: false })));
    await open();
    await click('btn-desactiver-2');
    expect(confirm).toHaveBeenCalledTimes(1);
    expect(confirm.mock.calls[0][0].message).toContain('Prénom2 Nom2');
    expect(deactivateUser).not.toHaveBeenCalled();
    confirm.mock.calls[0][0].accept();
    await whenStable();
    expect(deactivateUser).toHaveBeenCalledWith(2);
    expect(cy('statut-compte-2')?.textContent).toContain('Inactif');
    expect(cy('btn-reactiver-2')).not.toBeNull();
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
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
});
