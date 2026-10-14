import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import { Role, TeamAccountSummary, TeamResponse, TeamsService } from '../../../core/api/generated';
import { TeamsPage } from './teams';

const account = (id: number, over: Partial<TeamAccountSummary> = {}): TeamAccountSummary => ({
  id,
  firstName: `Prénom${id}`,
  lastName: `Nom${id}`,
  email: `u${id}@x.fr`,
  role: Role.User,
  active: true,
  ...over,
});

const team = (id: number, over: Partial<TeamResponse> = {}): TeamResponse => {
  const members = over.members ?? [];
  return { id, name: `Équipe ${id}`, manager: account(100 + id, { role: Role.Manager }), members, memberCount: members.length, version: 0, ...over };
};

describe('TeamsPage', () => {
  const listTeams = vi.fn();
  let root: HTMLElement;
  let whenStable: () => Promise<unknown>;

  async function open(): Promise<void> {
    TestBed.configureTestingModule({ providers: [{ provide: TeamsService, useValue: { listTeams } }, MessageService] });
    const fixture = TestBed.createComponent(TeamsPage);
    root = fixture.nativeElement as HTMLElement;
    whenStable = () => fixture.whenStable();
    await whenStable();
  }

  const cy = (name: string) => root.querySelector<HTMLElement>(`[data-cy="${name}"]`);

  beforeEach(() => {
    // jsdom n'a pas ResizeObserver, utilisé par la colonne figée (pFrozenColumn).
    vi.stubGlobal('ResizeObserver', class { observe = vi.fn(); unobserve = vi.fn(); disconnect = vi.fn(); });
    listTeams.mockReset();
    TestBed.resetTestingModule();
  });

  it('affiche l’état de chargement tant que la liste n’est pas arrivée', async () => {
    listTeams.mockReturnValue(new Subject());
    await open();
    expect(cy('etat-chargement')).not.toBeNull();
    expect(cy('table-equipes')).toBeNull();
  });

  it('affiche l’état vide avec l’action « Nouvelle équipe » quand il n’y a aucune équipe', async () => {
    listTeams.mockReturnValue(of([]));
    await open();
    expect(cy('etat-vide')).not.toBeNull();
    expect(cy('btn-nouvelle-equipe-vide')).not.toBeNull();
  });

  it('affiche l’état d’erreur avec « Réessayer », qui recharge la liste', async () => {
    listTeams.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })));
    await open();
    expect(cy('etat-erreur')?.textContent).toContain('Panne serveur');
    listTeams.mockReturnValue(of([team(1)]));
    cy('btn-reessayer')?.querySelector('button')?.click();
    await whenStable();
    expect(cy('table-equipes')).not.toBeNull();
    expect(cy('etat-erreur')).toBeNull();
  });

  it('CA-05-01 : l’équipe apparaît avec son nom, son manager et « 2 membres » ; ordre du back conservé (RG-05-13)', async () => {
    listTeams.mockReturnValue(
      of([
        team(2, { name: 'Produit', manager: account(7, { firstName: 'Ada', lastName: 'Martin', role: Role.Manager }), members: [account(3), account(4)], memberCount: 2 }),
        team(1, { name: 'Zèbre' }),
      ]),
    );
    await open();
    expect(listTeams).toHaveBeenCalledTimes(1);
    const rows = Array.from(root.querySelectorAll('[data-cy^="ligne-equipe-"]')).map((r) => r.getAttribute('data-cy'));
    expect(rows).toEqual(['ligne-equipe-2', 'ligne-equipe-1']);
    expect(cy('nom-equipe-2')?.textContent).toContain('Produit');
    expect(cy('manager-equipe-2')?.textContent).toContain('Ada Martin');
    expect(cy('membres-equipe-2')?.textContent?.trim()).toBe('2 membres');
    expect(cy('membres-equipe-1')?.textContent?.trim()).toBe('0 membre');
  });

  it('un seul membre s’écrit au singulier', async () => {
    listTeams.mockReturnValue(of([team(1, { members: [account(3)], memberCount: 1 })]));
    await open();
    expect(cy('membres-equipe-1')?.textContent?.trim()).toBe('1 membre');
  });

  it('le bouton « Nouvelle équipe » est présent dans l’en-tête', async () => {
    listTeams.mockReturnValue(of([team(1)]));
    await open();
    expect(cy('btn-nouvelle-equipe')).not.toBeNull();
  });
});
