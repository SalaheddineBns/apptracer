import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { AuthenticationService } from '../../../core/api/generated';
import { ChangePasswordPage } from './change-password';

describe('ChangePasswordPage', () => {
  const changeMyPassword = vi.fn();
  const add = vi.fn();
  let root: HTMLElement;
  let whenStable: () => Promise<unknown>;

  async function open(): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthenticationService, useValue: { changeMyPassword } },
        { provide: MessageService, useValue: { add } },
      ],
    });
    const fixture = TestBed.createComponent(ChangePasswordPage);
    root = fixture.nativeElement as HTMLElement;
    whenStable = () => fixture.whenStable();
    await whenStable();
  }

  const input = (cy: string) => root.querySelector<HTMLInputElement>(`[data-cy="${cy}"]`) as HTMLInputElement;
  const error = (cy: string) => root.querySelector<HTMLElement>(`[data-cy="erreur-${cy}"]`);

  async function fill(current: string, next: string, confirmation: string): Promise<void> {
    for (const [cy, value] of [
      ['champ-ancien-mot-de-passe', current],
      ['champ-nouveau-mot-de-passe', next],
      ['champ-confirmation-mot-de-passe', confirmation],
    ]) {
      input(cy).value = value;
      input(cy).dispatchEvent(new Event('input'));
    }
    await whenStable();
  }

  async function submit(): Promise<void> {
    root.querySelector<HTMLButtonElement>('[data-cy="btn-enregistrer"] button')?.click();
    await whenStable();
  }

  const http = (status: number, body: object) => throwError(() => new HttpErrorResponse({ status, error: body }));

  beforeEach(() => {
    changeMyPassword.mockReset();
    add.mockReset();
  });

  it('CA-03-14 : ancien mot de passe incorrect, l’erreur RG-03-15 s’affiche sous le champ, sans toast', async () => {
    changeMyPassword.mockReturnValue(
      http(409, { status: 409, detail: 'Ancien mot de passe incorrect.', code: 'RG-03-15', errors: [{ field: 'currentPassword', message: 'L’ancien mot de passe est incorrect.' }] }),
    );
    await open();
    await fill('mauvais', 'nouveau-mdp-123', 'nouveau-mdp-123');
    await submit();
    expect(changeMyPassword).toHaveBeenCalledWith({ currentPassword: 'mauvais', newPassword: 'nouveau-mdp-123' });
    expect(error('champ-ancien-mot-de-passe')?.textContent).toContain('L’ancien mot de passe est incorrect.');
    expect(add).not.toHaveBeenCalled();
    expect(input('champ-nouveau-mot-de-passe').value).toBe('nouveau-mdp-123');
  });

  it('refuse une confirmation différente du nouveau mot de passe, sans appel API', async () => {
    await open();
    await fill('ancien', 'nouveau-mdp-123', 'autre-mdp-12345');
    await submit();
    expect(changeMyPassword).not.toHaveBeenCalled();
    expect(error('champ-confirmation-mot-de-passe')?.textContent).toContain('identique');
  });

  it('RG-03-02 : refuse un nouveau mot de passe trop court, sans lettre ou sans chiffre', async () => {
    await open();
    for (const [value, message] of [
      ['court1', 'au moins 12 caractères'],
      ['123456789012', 'au moins une lettre'],
      ['abcdefghijkl', 'au moins un chiffre'],
    ]) {
      await fill('ancien', value, value);
      await submit();
      expect(error('champ-nouveau-mot-de-passe')?.textContent).toContain(message);
    }
    expect(changeMyPassword).not.toHaveBeenCalled();
  });

  it('affiche sous le champ une erreur 400 du back sur newPassword', async () => {
    changeMyPassword.mockReturnValue(http(400, { status: 400, detail: 'Requête invalide', errors: [{ field: 'newPassword', message: 'Refusé par le serveur.' }] }));
    await open();
    await fill('ancien', 'nouveau-mdp-123', 'nouveau-mdp-123');
    await submit();
    expect(error('champ-nouveau-mot-de-passe')?.textContent).toContain('Refusé par le serveur.');
  });

  it('succès : toast de confirmation, formulaire vidé, aucune déconnexion', async () => {
    changeMyPassword.mockReturnValue(of(null));
    await open();
    await fill('ancien', 'nouveau-mdp-123', 'nouveau-mdp-123');
    await submit();
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', life: 3000 }));
    expect(input('champ-ancien-mot-de-passe').value).toBe('');
  });

  it('succès : le formulaire revient à son état initial, sans erreur à la tabulation suivante, et les champs repartent masqués', async () => {
    changeMyPassword.mockReturnValue(of(null));
    await open();
    await fill('ancien', 'nouveau-mdp-123', 'nouveau-mdp-123');
    root.querySelector<HTMLButtonElement>('[data-cy="btn-afficher-nouveau-mot-de-passe"] button')?.click();
    await whenStable();
    expect(input('champ-nouveau-mot-de-passe').type).toBe('text');
    input('champ-confirmation-mot-de-passe').focus();
    await submit();
    // Une tabulation retire le focus du champ actif.
    if (document.activeElement instanceof HTMLElement) {
      document.activeElement.blur();
    }
    await whenStable();
    expect(error('champ-confirmation-mot-de-passe')).toBeNull();
    expect(input('champ-nouveau-mot-de-passe').type).toBe('password');
  });

  it('chaque champ a un bouton Afficher / Masquer au nom accessible distinct', async () => {
    await open();
    const labels = Array.from(root.querySelectorAll('[data-cy^="btn-afficher-"] button')).map((b) => b.getAttribute('aria-label'));
    expect(labels).toEqual(['Afficher l’ancien mot de passe', 'Afficher le nouveau mot de passe', 'Afficher la confirmation']);
    root.querySelector<HTMLButtonElement>('[data-cy="btn-afficher-nouveau-mot-de-passe"] button')?.click();
    await whenStable();
    expect(input('champ-nouveau-mot-de-passe').type).toBe('text');
    expect(input('champ-ancien-mot-de-passe').type).toBe('password');
  });
});
