import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import { Role, UserResponse, UsersService } from '../../../core/api/generated';
import { AuthStore } from '../../../core/auth/auth.store';
import { AccountDialog } from './account-dialog';

const existing = (over: Partial<UserResponse> = {}): UserResponse => ({
  id: 2,
  email: 'bob@x.fr',
  firstName: 'Bob',
  lastName: 'Durand',
  role: Role.User,
  active: true,
  version: 3,
  ...over,
});
const httpError = (status: number, body: object) => throwError(() => new HttpErrorResponse({ status, error: body }));

describe('AccountDialog', () => {
  const createUser = vi.fn();
  const updateUser = vi.fn();
  const listUsers = vi.fn();
  const add = vi.fn();
  let fixture: ReturnType<typeof TestBed.createComponent<AccountDialog>>;
  const saved = vi.fn();
  const conflict = vi.fn();

  async function open(account: UserResponse | null): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        { provide: UsersService, useValue: { createUser, updateUser, listUsers } },
        { provide: AuthStore, useValue: { user: signal({ id: 1, email: 'a@x.fr', firstName: 'Ada', lastName: 'Admin', role: Role.Admin }) } },
        { provide: MessageService, useValue: { add } },
      ],
    });
    fixture = TestBed.createComponent(AccountDialog);
    fixture.componentInstance.saved.subscribe(saved);
    fixture.componentInstance.conflict.subscribe(conflict);
    fixture.componentRef.setInput('account', account);
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
  async function fillCreate(email = 'new@x.fr', password = 'motdepasse-123'): Promise<void> {
    await type('champ-prenom', 'Nina');
    await type('champ-nom', 'Noir');
    await type('champ-email-compte', email);
    await type('champ-mot-de-passe-initial', password);
  }

  beforeEach(() => {
    for (const fn of [createUser, updateUser, listUsers, add, saved, conflict]) {
      fn.mockReset();
    }
    listUsers.mockReturnValue(of({ content: [], page: 0, size: 20, totalElements: 0 }));
    TestBed.resetTestingModule();
    document.body.querySelectorAll('p-dialog, .p-dialog-mask').forEach((n) => n.remove());
  });

  it('création réussie : envoie les cinq champs, ferme le dialogue et signale la création', async () => {
    createUser.mockReturnValue(of(existing({ id: 9, email: 'new@x.fr', firstName: 'Nina', lastName: 'Noir' })));
    await open(null);
    await fillCreate();
    await submit();
    expect(createUser).toHaveBeenCalledWith({ firstName: 'Nina', lastName: 'Noir', email: 'new@x.fr', role: Role.User, initialPassword: 'motdepasse-123' });
    expect(saved).toHaveBeenCalledWith(expect.objectContaining({ mode: 'create' }));
    expect(fixture.componentInstance.visible()).toBe(false);
    expect(listUsers).toHaveBeenCalled();
  });

  it('CA-03-10 : e-mail déjà utilisé, l’erreur RG-03-01 s’affiche sous le champ e-mail, dialogue ouvert et saisie conservée', async () => {
    createUser.mockReturnValue(
      httpError(409, { status: 409, code: 'RG-03-01', detail: 'Cet e-mail est déjà utilisé.', errors: [{ field: 'email', message: 'Cet e-mail est déjà utilisé.' }] }),
    );
    await open(null);
    await fillCreate('dup@x.fr');
    await submit();
    expect(error('champ-email-compte')?.textContent).toContain('déjà utilisé');
    expect(fixture.componentInstance.visible()).toBe(true);
    expect(input('champ-email-compte').value).toBe('dup@x.fr');
    expect(input('champ-prenom').value).toBe('Nina');
    expect(add).not.toHaveBeenCalled();
    expect(saved).not.toHaveBeenCalled();
  });

  it('CA-03-11 : mot de passe initial de 8 caractères, champ en erreur et enregistrement bloqué', async () => {
    await open(null);
    await fillCreate('new@x.fr', 'abcd1234');
    await submit();
    expect(createUser).not.toHaveBeenCalled();
    expect(error('champ-mot-de-passe-initial')?.textContent).toContain('au moins 12 caractères');
    expect(fixture.componentInstance.visible()).toBe(true);
  });

  it('affiche sous le champ une erreur 400 du back sur initialPassword', async () => {
    createUser.mockReturnValue(httpError(400, { status: 400, detail: 'Requête invalide', errors: [{ field: 'initialPassword', message: 'Refusé par le serveur.' }] }));
    await open(null);
    await fillCreate();
    await submit();
    expect(error('champ-mot-de-passe-initial')?.textContent).toContain('Refusé par le serveur.');
  });

  it('refuse un prénom de plus de 100 caractères', async () => {
    await open(null);
    await fillCreate();
    await type('champ-prenom', 'x'.repeat(101));
    await submit();
    expect(createUser).not.toHaveBeenCalled();
    expect(error('champ-prenom')?.textContent).toContain('100 caractères');
  });

  it('modification réussie : e-mail non modifiable, pas de mot de passe, version envoyée', async () => {
    updateUser.mockReturnValue(of(existing({ firstName: 'Robert', version: 4 })));
    await open(existing());
    expect(input('champ-email-compte').disabled).toBe(true);
    expect(input('champ-email-compte').value).toBe('bob@x.fr');
    expect(el('champ-mot-de-passe-initial')).toBeNull();
    await type('champ-prenom', 'Robert');
    await submit();
    expect(updateUser).toHaveBeenCalledWith(2, { firstName: 'Robert', lastName: 'Durand', role: Role.User, version: 3 });
    expect(saved).toHaveBeenCalledWith(expect.objectContaining({ mode: 'update' }));
    expect(fixture.componentInstance.visible()).toBe(false);
  });

  it('RG-03-13 : le rôle est verrouillé sur son propre compte et conservé à l’envoi', async () => {
    updateUser.mockReturnValue(of(existing({ id: 1, role: Role.Admin })));
    await open(existing({ id: 1, role: Role.Admin }));
    expect(fixture.componentInstance['form'].controls.role.disabled).toBe(true);
    await submit();
    expect(updateUser).toHaveBeenCalledWith(1, expect.objectContaining({ role: Role.Admin }));
  });

  it('RG-03-13 : un 409 de l’API sur le champ role s’affiche sous le champ rôle', async () => {
    updateUser.mockReturnValue(httpError(409, { status: 409, code: 'RG-03-13', detail: 'Rôle non modifiable.', errors: [{ field: 'role', message: 'Vous ne pouvez pas retirer votre rôle ADMIN.' }] }));
    await open(existing());
    await submit();
    expect(error('champ-role')?.textContent).toContain('retirer votre rôle');
    expect(fixture.componentInstance.visible()).toBe(true);
  });

  it('modification concurrente : 409 sans code, le dialogue se ferme et la liste est rechargée', async () => {
    updateUser.mockReturnValue(httpError(409, { status: 409, type: 'https://apptracer/errors/concurrent-modification', detail: 'Ce compte a été modifié entre-temps.' }));
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
    expect(createUser).not.toHaveBeenCalled();
  });

  it('le mot de passe initial repart masqué à chaque ouverture, même après « Afficher »', async () => {
    await open(null);
    el('btn-afficher-mot-de-passe-initial')?.querySelector('button')?.click();
    await fixture.whenStable();
    expect(input('champ-mot-de-passe-initial').type).toBe('text');
    fixture.componentInstance.visible.set(false);
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 300));
    fixture.componentInstance.visible.set(true);
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 300));
    expect(input('champ-mot-de-passe-initial').type).toBe('password');
  });

  it('pendant l’envoi, le dialogue ne peut être fermé ni par Échap, ni par « Annuler », ni par la croix', async () => {
    createUser.mockReturnValue(new Subject());
    await open(null);
    await fillCreate();
    await submit();
    expect(createUser).toHaveBeenCalledTimes(1);
    expect(el('btn-annuler-dialogue')?.querySelector('button')?.disabled).toBe(true);
    expect(document.body.querySelector('.p-dialog-close-button')).toBeNull();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }));
    el('dialogue-compte')?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }));
    await fixture.whenStable();
    expect(fixture.componentInstance.visible()).toBe(true);
  });

  it('la croix du dialogue a un nom accessible « Fermer » quand aucun envoi n’est en cours', async () => {
    await open(null);
    expect(document.body.querySelector('.p-dialog-close-button')?.getAttribute('aria-label')).toBe('Fermer');
  });

  it("ne reprend pas le focus à la fin de l'animation si la saisie a déjà commencé", async () => {
    await open(null);
    const dialog = fixture.componentInstance as unknown as { focusFirstField(field: HTMLInputElement): void };

    input('champ-email-compte').focus();
    dialog.focusFirstField(input('champ-prenom'));
    expect(document.activeElement).toBe(input('champ-email-compte'));

    (document.activeElement as HTMLElement).blur();
    dialog.focusFirstField(input('champ-prenom'));
    expect(document.activeElement).toBe(input('champ-prenom'));
  });
});
