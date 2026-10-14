import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
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
  const deleteTeam = vi.fn();
  const confirm = vi.fn();
  const add = vi.fn();
  let fixtureRef: ReturnType<typeof TestBed.createComponent<TeamsPage>>;
  let root: HTMLElement;
  let whenStable: () => Promise<unknown>;

  async function open(): Promise<void> {
    TestBed.configureTestingModule({ providers: [{ provide: TeamsService, useValue: { listTeams, deleteTeam, listTeamManagerCandidates: () => of([]), listTeamMemberCandidates: () => of([]) } }, { provide: ConfirmationService, useValue: { confirm } }, { provide: MessageService, useValue: { add } }] });
    const fixture = TestBed.createComponent(TeamsPage);
    fixtureRef = fixture;
    root = fixture.nativeElement as HTMLElement;
    whenStable = () => fixture.whenStable();
    await whenStable();
  }

  const cy = (name: string) => root.querySelector<HTMLElement>(`[data-cy="${name}"]`);

  beforeEach(() => {
    // jsdom n'a pas ResizeObserver, utilisé par la colonne figée (pFrozenColumn).
    vi.stubGlobal('ResizeObserver', class { observe = vi.fn(); unobserve = vi.fn(); disconnect = vi.fn(); });
    listTeams.mockReset();
    add.mockReset();
    deleteTeam.mockReset();
    confirm.mockReset();
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

  it('« Modifier » ouvre le dialogue E2 avec l’équipe de la ligne, « Nouvelle équipe » le rouvre en création', async () => {
    listTeams.mockReturnValue(of([team(1, { name: 'Produit' })]));
    await open();
    expect(cy('btn-modifier-equipe-1')?.querySelector('button')?.getAttribute('aria-label')).toBe('Modifier l’équipe Produit');
    const page = fixtureRef.componentInstance as unknown as { dialogVisible(): boolean; editing(): unknown };
    cy('btn-modifier-equipe-1')?.querySelector('button')?.click();
    await whenStable();
    expect(page.dialogVisible()).toBe(true);
    expect(page.editing()).toEqual(expect.objectContaining({ id: 1 }));
    cy('btn-nouvelle-equipe')?.querySelector('button')?.click();
    await whenStable();
    expect(page.editing()).toBeNull();
  });

  it('le toast de création a le titre « Équipe créée », celui d’une modification « Équipe mise à jour »', async () => {
    listTeams.mockReturnValue(of([]));
    await open();
    const page = fixtureRef.componentInstance as unknown as { onSaved(e: { mode: 'create' | 'update'; team: TeamResponse }): void };
    page.onSaved({ mode: 'create', team: team(1) });
    page.onSaved({ mode: 'update', team: team(1) });
    expect(add.mock.calls.map((c) => c[0].summary)).toEqual(['Équipe créée', 'Équipe mise à jour']);
  });

  describe('E3 Suppression', () => {
    const threeMembers = [account(3), account(4), account(5)];

    it('CA-05-09 : la confirmation cite le nom et le nombre de membres ; une fois confirmée, l’équipe disparaît de la liste et seule l’API d’équipe est appelée', async () => {
      listTeams.mockReturnValue(of([team(1, { name: 'Produit', members: threeMembers, memberCount: 3 }), team(2, { name: 'Zèbre' })]));
      deleteTeam.mockReturnValue(of(null));
      await open();
      cy('btn-supprimer-equipe-1')?.querySelector('button')?.click();
      await whenStable();
      expect(confirm).toHaveBeenCalledTimes(1);
      const options = confirm.mock.calls[0][0];
      expect(options.message).toContain('« Produit »');
      expect(options.message).toContain('Ses 3 membres se retrouveront sans équipe.');
      expect(options.message).toContain('définitive');
      expect(options.acceptLabel).toBe('Supprimer');
      expect(options.closable).not.toBe(false);
      expect(deleteTeam).not.toHaveBeenCalled();
      options.accept();
      await whenStable();
      expect(deleteTeam).toHaveBeenCalledWith(1);
      expect(cy('ligne-equipe-1')).toBeNull();
      expect(cy('ligne-equipe-2')).not.toBeNull();
      expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', summary: 'Équipe supprimée', life: 3000 }));
      expect(listTeams).toHaveBeenCalledTimes(1);
    });

    it('annulée, la confirmation n’appelle pas l’API et la ligne reste', async () => {
      listTeams.mockReturnValue(of([team(1)]));
      await open();
      cy('btn-supprimer-equipe-1')?.querySelector('button')?.click();
      await whenStable();
      expect(deleteTeam).not.toHaveBeenCalled();
      expect(cy('ligne-equipe-1')).not.toBeNull();
    });

    it.each([
      [0, 'Elle n’a aucun membre.'],
      [1, 'Son membre se retrouvera sans équipe.'],
    ])('la confirmation d’une équipe de %s membre(s) est accordée : « %s »', async (count, sentence) => {
      listTeams.mockReturnValue(of([team(1, { members: threeMembers.slice(0, count), memberCount: count })]));
      await open();
      cy('btn-supprimer-equipe-1')?.querySelector('button')?.click();
      await whenStable();
      expect(confirm.mock.calls[0][0].message).toContain(sentence);
    });

    it('le nom du bouton nomme l’équipe', async () => {
      listTeams.mockReturnValue(of([team(1, { name: 'Produit' })]));
      await open();
      expect(cy('btn-supprimer-equipe-1')?.querySelector('button')?.getAttribute('aria-label')).toBe('Supprimer l’équipe Produit');
    });

    it('F1 : une suppression confirmée deux fois n’envoie qu’une requête, bouton désactivé jusqu’à la réponse', async () => {
      listTeams.mockReturnValue(of([team(1)]));
      const response = new Subject<null>();
      deleteTeam.mockReturnValue(response);
      await open();
      cy('btn-supprimer-equipe-1')?.querySelector('button')?.click();
      await whenStable();
      const { accept } = confirm.mock.calls[0][0];
      accept();
      accept();
      await whenStable();
      expect(deleteTeam).toHaveBeenCalledTimes(1);
      expect((cy('btn-supprimer-equipe-1')?.querySelector('button') as HTMLButtonElement).disabled).toBe(true);
      response.next(null);
      response.complete();
      await whenStable();
      expect(cy('ligne-equipe-1')).toBeNull();
    });

    it('équipe déjà supprimée (404) : la ligne n’est pas retirée localement, la liste est relue', async () => {
      listTeams.mockReturnValueOnce(of([team(1), team(2)])).mockReturnValue(of([team(2)]));
      deleteTeam.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404, error: { detail: 'Équipe introuvable.' } })));
      await open();
      cy('btn-supprimer-equipe-1')?.querySelector('button')?.click();
      await whenStable();
      confirm.mock.calls[0][0].accept();
      await whenStable();
      expect(listTeams).toHaveBeenCalledTimes(2);
      expect(cy('ligne-equipe-1')).toBeNull();
      expect(add).not.toHaveBeenCalled();
    });
  });
});
