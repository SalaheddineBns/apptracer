import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { PRIMENG_FR } from '../../../core/theme/primeng-fr';
import { of, Subject, throwError } from 'rxjs';
import { Role, TeamAccountSummary, TeamMemberCandidateResponse, TeamResponse, TeamsService } from '../../../core/api/generated';
import { TeamDialog } from './team-dialog';

const manager = (id: number, over: Partial<TeamAccountSummary> = {}): TeamAccountSummary => ({
  id,
  firstName: `Manon${id}`,
  lastName: `Dupont${id}`,
  email: `manager${id}@x.fr`,
  role: Role.Manager,
  active: true,
  ...over,
});
const candidate = (id: number, over: Partial<TeamMemberCandidateResponse> = {}): TeamMemberCandidateResponse => ({
  id,
  firstName: `Paul${id}`,
  lastName: `Martin${id}`,
  email: `user${id}@x.fr`,
  role: Role.User,
  active: true,
  ...over,
});
const created = (over: Partial<TeamResponse> = {}): TeamResponse => ({ id: 9, name: 'Produit', manager: manager(1), members: [], memberCount: 0, version: 0, ...over });
const httpError = (status: number, body: object) => throwError(() => new HttpErrorResponse({ status, error: body }));

const existingTeam = (over: Partial<TeamResponse> = {}): TeamResponse => {
  const members = over.members ?? [
    { id: 10, firstName: 'Paul10', lastName: 'Martin10', email: 'user10@x.fr', role: Role.User, active: true },
    { id: 11, firstName: 'Paul11', lastName: 'Martin11', email: 'user11@x.fr', role: Role.User, active: false },
  ];
  return { id: 4, name: 'Produit', manager: manager(1), members, memberCount: members.length, version: 3, ...over };
};

describe('TeamDialog', () => {
  const createTeam = vi.fn();
  const updateTeam = vi.fn();
  const listTeams = vi.fn();
  const listTeamManagerCandidates = vi.fn();
  const listTeamMemberCandidates = vi.fn();
  const add = vi.fn();
  const saved = vi.fn();
  const conflict = vi.fn();
  let fixture: ReturnType<typeof TestBed.createComponent<TeamDialog>>;

  async function open(team: TeamResponse | null = null): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        { provide: TeamsService, useValue: { createTeam, updateTeam, listTeams, listTeamManagerCandidates, listTeamMemberCandidates } },
        { provide: MessageService, useValue: { add } },
        providePrimeNG({ translation: PRIMENG_FR }),
      ],
    });
    fixture = TestBed.createComponent(TeamDialog);
    fixture.componentInstance.saved.subscribe(saved);
    fixture.componentInstance.conflict.subscribe(conflict);
    fixture.componentRef.setInput('team', team);
    fixture.componentRef.setInput('visible', true);
    await fixture.whenStable();
    await fixture.whenStable();
  }

  const el = (cy: string) => document.body.querySelector<HTMLElement>(`[data-cy="${cy}"]`);
  const input = (cy: string) => el(cy) as HTMLInputElement;
  const error = (cy: string) => el(`erreur-${cy}`);
  const form = () => (fixture.componentInstance as unknown as { form: { controls: Record<string, { value: unknown; setValue(v: unknown): void }> } }).form;
  async function type(cy: string, value: string): Promise<void> {
    input(cy).value = value;
    input(cy).dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }
  async function submit(): Promise<void> {
    el('btn-enregistrer')?.querySelector('button')?.click();
    await fixture.whenStable();
  }
  /** Opens the dropdown (the panel is rendered in the body), then clicks an option by its data-cy. */
  async function pick(field: 'champ-manager' | 'champ-membres', optionCy: string): Promise<void> {
    if (!el(optionCy)) {
      el(field)?.click();
      await fixture.whenStable();
    }
    el(optionCy)?.click();
    await fixture.whenStable();
  }
  async function fillValid(): Promise<void> {
    await type('champ-nom-equipe', 'Produit');
    await pick('champ-manager', 'option-manager-1');
  }

  beforeEach(() => {
    // jsdom n'a pas matchMedia, utilisé par les panneaux déroulants de PrimeNG (p-select, p-multiselect), ainsi que scrollIntoView.
    vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn() }));
    HTMLElement.prototype.scrollIntoView = vi.fn();
    for (const fn of [createTeam, updateTeam, conflict, listTeams, listTeamManagerCandidates, listTeamMemberCandidates, add, saved]) {
      fn.mockReset();
    }
    listTeams.mockReturnValue(of([]));
    listTeamManagerCandidates.mockReturnValue(of([manager(1), manager(2)]));
    listTeamMemberCandidates.mockReturnValue(of([candidate(10), candidate(11), candidate(12)]));
    TestBed.resetTestingModule();
    document.body.querySelectorAll('p-dialog, .p-dialog-mask, .p-overlay').forEach((n) => n.remove());
  });

  it('CA-05-01 : crée l’équipe avec un manager et deux membres, ferme le dialogue et recharge la liste', async () => {
    createTeam.mockReturnValue(of(created({ memberCount: 2 })));
    await open();
    await fillValid();
    await pick('champ-membres', 'option-membre-10');
    await pick('champ-membres', 'option-membre-11');
    await submit();
    expect(createTeam).toHaveBeenCalledWith({ name: 'Produit', managerId: 1, memberIds: [10, 11] });
    expect(saved).toHaveBeenCalledWith(expect.objectContaining({ mode: 'create' }));
    expect(fixture.componentInstance.visible()).toBe(false);
    expect(listTeams).toHaveBeenCalled();
  });

  it('RG-05-07 : une équipe sans membre est acceptée', async () => {
    createTeam.mockReturnValue(of(created()));
    await open();
    await fillValid();
    await submit();
    expect(createTeam).toHaveBeenCalledWith({ name: 'Produit', managerId: 1, memberIds: [] });
    expect(saved).toHaveBeenCalled();
  });

  it('CA-05-02 : nom déjà utilisé (409 RG-05-01), l’erreur s’affiche sous le champ nom, dialogue ouvert et saisie conservée', async () => {
    createTeam.mockReturnValue(
      httpError(409, { status: 409, code: 'RG-05-01', detail: 'Ce nom d’équipe est déjà utilisé.', errors: [{ field: 'name', message: 'Ce nom d’équipe est déjà utilisé.' }] }),
    );
    await open();
    await type('champ-nom-equipe', 'PRODUIT');
    await pick('champ-manager', 'option-manager-1');
    await submit();
    expect(error('champ-nom-equipe')?.textContent).toContain('déjà utilisé');
    expect(fixture.componentInstance.visible()).toBe(true);
    expect(input('champ-nom-equipe').value).toBe('PRODUIT');
    expect(form().controls['managerId'].value).toBe(1);
    expect(add).not.toHaveBeenCalled();
    expect(saved).not.toHaveBeenCalled();
  });

  it('CA-05-03 : seuls les comptes renvoyés par listTeamManagerCandidates sont proposés comme manager (aucun USER)', async () => {
    await open();
    el('champ-manager')?.click();
    await fixture.whenStable();
    const options = Array.from(document.body.querySelectorAll('[data-cy^="option-manager-"]')).map((o) => o.getAttribute('data-cy'));
    expect(options).toEqual(['option-manager-1', 'option-manager-2']);
    expect(listTeamManagerCandidates).toHaveBeenCalledTimes(1);
  });

  it('CA-05-03 : 409 RG-05-02 sur managerId, affiché sous le champ manager', async () => {
    createTeam.mockReturnValue(
      httpError(409, { status: 409, code: 'RG-05-02', detail: 'Le manager doit être un compte actif de rôle MANAGER.', errors: [{ field: 'managerId', message: 'Le manager doit être un compte actif de rôle MANAGER.' }] }),
    );
    await open();
    await fillValid();
    await submit();
    expect(error('champ-manager')?.textContent).toContain('rôle MANAGER');
    expect(fixture.componentInstance.visible()).toBe(true);
  });

  it('CA-05-04 : un compte déjà membre d’une autre équipe est signalé avec le nom de cette équipe mais reste sélectionnable ; le 409 RG-05-04 s’affiche sous les membres', async () => {
    listTeamMemberCandidates.mockReturnValue(of([candidate(10, { currentTeam: { id: 4, name: 'Alpha' } }), candidate(11)]));
    createTeam.mockReturnValue(
      httpError(409, { status: 409, code: 'RG-05-04', detail: 'Paul10 Martin10 est déjà membre de l’équipe Alpha.', errors: [{ field: 'memberIds', message: 'Paul10 Martin10 est déjà membre de l’équipe Alpha.' }] }),
    );
    await open();
    el('champ-membres')?.click();
    await fixture.whenStable();
    expect(el('option-membre-10')?.textContent).toContain('Déjà dans l’équipe Alpha');
    expect(el('option-membre-11')?.textContent).not.toContain('Déjà dans l’équipe');
    await fillValid();
    await pick('champ-membres', 'option-membre-10');
    expect(form().controls['memberIds'].value).toEqual([10]);
    await submit();
    expect(error('champ-membres')?.textContent).toContain('Alpha');
    expect(fixture.componentInstance.visible()).toBe(true);
  });

  it('CA-05-05 : seuls les comptes renvoyés par listTeamMemberCandidates sont proposés comme membres (aucun ADMIN) ; 409 RG-05-03 sous les membres', async () => {
    createTeam.mockReturnValue(
      httpError(409, { status: 409, code: 'RG-05-03', detail: 'Un administrateur ne peut pas être membre.', errors: [{ field: 'memberIds', message: 'Un administrateur ne peut pas être membre.' }] }),
    );
    await open();
    el('champ-membres')?.click();
    await fixture.whenStable();
    const options = Array.from(document.body.querySelectorAll('[data-cy^="option-membre-"]')).map((o) => o.getAttribute('data-cy'));
    expect(options).toEqual(['option-membre-10', 'option-membre-11', 'option-membre-12']);
    await fillValid();
    await submit();
    expect(error('champ-membres')?.textContent).toContain('administrateur');
  });

  it('CA-05-06 : 409 RG-05-05 sur memberIds, affiché sous les membres', async () => {
    createTeam.mockReturnValue(
      httpError(409, { status: 409, code: 'RG-05-05', detail: 'Le manager ne peut pas être membre de son équipe.', errors: [{ field: 'memberIds', message: 'Le manager ne peut pas être membre de son équipe.' }] }),
    );
    await open();
    await fillValid();
    await submit();
    expect(error('champ-membres')?.textContent).toContain('manager');
  });

  it('RG-05-05 : le compte choisi comme manager est retiré des membres proposés et décoché s’il était déjà membre, avec une information', async () => {
    listTeamManagerCandidates.mockReturnValue(of([manager(1)]));
    listTeamMemberCandidates.mockReturnValue(of([candidate(1, { role: Role.Manager, firstName: 'Manon1', lastName: 'Dupont1' }), candidate(10)]));
    await open();
    await pick('champ-membres', 'option-membre-1');
    expect(form().controls['memberIds'].value).toEqual([1]);
    await pick('champ-manager', 'option-manager-1');
    expect(form().controls['memberIds'].value).toEqual([]);
    expect(el('info-membre-retire')?.textContent).toContain('Manon1 Dupont1');
    el('champ-membres')?.click();
    await fixture.whenStable();
    expect(el('option-membre-1')).toBeNull();
    expect(el('option-membre-10')).not.toBeNull();
  });

  it('le déclencheur de la liste des membres porte un data-cy (le centre du champ peut tomber sur une pastille supprimable)', async () => {
    await open();
    expect(el('btn-ouvrir-membres')).not.toBeNull();
    el('btn-ouvrir-membres')?.click();
    await fixture.whenStable();
    expect(el('option-membre-10')).not.toBeNull();
  });

  it('RG-05-11 : un compte inactif est proposé et signalé « Inactif »', async () => {
    listTeamMemberCandidates.mockReturnValue(of([candidate(10, { active: false }), candidate(11)]));
    await open();
    el('champ-membres')?.click();
    await fixture.whenStable();
    expect(el('option-membre-10')?.textContent).toContain('Inactif');
    expect(el('option-membre-11')?.textContent).not.toContain('Inactif');
  });

  it('B1 : à 3 membres et plus, toutes les pastilles restent visibles et aucun « items selected » n’apparaît', async () => {
    const members = [10, 11, 12, 13].map((id) => ({ id, firstName: `Paul${id}`, lastName: `Martin${id}`, email: `user${id}@x.fr`, role: Role.User, active: id !== 12 }));
    listTeamMemberCandidates.mockReturnValue(of(members.map((m) => candidate(m.id, { active: m.active }))));
    await open(existingTeam({ members }));
    const chips = Array.from(el('champ-membres')?.querySelectorAll('.p-chip-label') ?? []).map((c) => c.textContent?.trim());
    expect(chips).toEqual(['Paul10 Martin10', 'Paul11 Martin11', 'Paul12 Martin12 (inactif)', 'Paul13 Martin13']);
    expect(el('champ-membres')?.textContent).not.toContain('items selected');
    expect(el('champ-membres')?.textContent).not.toContain('sélectionnés');
  });

  it('B4 : le libellé de l’option et de la pastille annonce l’équipe actuelle du compte, comme « (inactif) »', async () => {
    listTeamMemberCandidates.mockReturnValue(of([candidate(10, { currentTeam: { id: 8, name: 'Alpha' } }), candidate(11, { active: false }), candidate(12)]));
    await open();
    await pick('champ-membres', 'option-membre-10');
    const label = (id: number) => el(`option-membre-${id}`)?.closest('li')?.getAttribute('aria-label');
    expect(label(10)).toBe('Paul10 Martin10 (équipe Alpha)');
    expect(label(11)).toBe('Paul11 Martin11 (inactif)');
    expect(label(12)).toBe('Paul12 Martin12');
    expect(el('champ-membres')?.querySelector('.p-chip-label')?.textContent?.trim()).toBe('Paul10 Martin10 (équipe Alpha)');
  });

  it('B5 : le membre retiré parce qu’il devient manager est signalé par un p-message d’information', async () => {
    listTeamManagerCandidates.mockReturnValue(of([manager(1)]));
    listTeamMemberCandidates.mockReturnValue(of([candidate(1, { role: Role.Manager, firstName: 'Manon1', lastName: 'Dupont1' }), candidate(10)]));
    await open();
    await pick('champ-membres', 'option-membre-1');
    await pick('champ-manager', 'option-manager-1');
    const info = el('info-membre-retire');
    expect(info?.tagName).toBe('P-MESSAGE');
    expect(info?.classList.contains('p-message-info')).toBe(true);
  });

  it('M3 : les textes de PrimeNG sont en français (croix d’une pastille)', async () => {
    await open(existingTeam());
    expect(el('champ-membres')?.querySelector('.p-chip-remove-icon')?.getAttribute('aria-label')).toBe('Retirer');
  });

  it('B3 : « Réessayer » rend le focus au premier champ du dialogue au lieu de le perdre avec le bouton détruit', async () => {
    listTeamManagerCandidates.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })));
    await open();
    el('btn-reessayer')?.querySelector('button')?.click();
    await fixture.whenStable();
    expect(el('btn-reessayer')).toBeNull();
    expect(document.activeElement).toBe(input('champ-nom-equipe'));
  });

  it('RG-05-01 : un nom d’un caractère est refusé, sans appel à l’API', async () => {
    await open();
    await type('champ-nom-equipe', 'P');
    await pick('champ-manager', 'option-manager-1');
    await submit();
    expect(createTeam).not.toHaveBeenCalled();
    expect(error('champ-nom-equipe')?.textContent).toContain('au moins 2 caractères');
  });

  it('RG-05-01 : un nom de 101 caractères est refusé', async () => {
    await open();
    await type('champ-nom-equipe', 'x'.repeat(101));
    await pick('champ-manager', 'option-manager-1');
    await submit();
    expect(createTeam).not.toHaveBeenCalled();
    expect(error('champ-nom-equipe')?.textContent).toContain('100 caractères');
  });

  it('RG-05-01 : la saisie n’est jamais modifiée en silence, les espaces de bord sont envoyés tels quels (le back tranche)', async () => {
    createTeam.mockReturnValue(of(created()));
    await open();
    await type('champ-nom-equipe', '  Produit  ');
    await pick('champ-manager', 'option-manager-1');
    await submit();
    expect(createTeam).toHaveBeenCalledWith({ name: '  Produit  ', managerId: 1, memberIds: [] });
  });

  it('RG-05-01 : une erreur 400 du back sur name (nom trop court après retrait des espaces) s’affiche sous le champ', async () => {
    createTeam.mockReturnValue(httpError(400, { status: 400, detail: 'Requête invalide', errors: [{ field: 'name', message: 'Le nom doit comporter entre 2 et 100 caractères.' }] }));
    await open();
    await type('champ-nom-equipe', ' a ');
    await pick('champ-manager', 'option-manager-1');
    await submit();
    expect(error('champ-nom-equipe')?.textContent).toContain('entre 2 et 100');
    expect(input('champ-nom-equipe').value).toBe(' a ');
  });

  it('RG-05-02 : sans manager, l’enregistrement est bloqué et le champ manager est en erreur', async () => {
    await open();
    await type('champ-nom-equipe', 'Produit');
    await submit();
    expect(createTeam).not.toHaveBeenCalled();
    expect(error('champ-manager')?.textContent).toContain('obligatoire');
  });

  it('affiche un état de chargement tant que les candidats ne sont pas arrivés', async () => {
    listTeamManagerCandidates.mockReturnValueOnce(new Subject());
    await open();
    expect(el('etat-chargement')).not.toBeNull();
    expect(el('champ-manager')).toBeNull();
  });

  it('erreur de chargement des candidats : message, « Réessayer » recharge les deux listes', async () => {
    listTeamManagerCandidates.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })));
    await open();
    expect(el('etat-erreur')?.textContent).toContain('Panne serveur');
    expect(el('btn-enregistrer')?.querySelector('button')?.disabled).toBe(true);
    el('btn-reessayer')?.querySelector('button')?.click();
    await fixture.whenStable();
    expect(el('etat-erreur')).toBeNull();
    expect(el('champ-manager')).not.toBeNull();
  });

  it('pendant l’envoi, un second clic n’envoie pas de seconde requête et le dialogue ne peut être fermé', async () => {
    createTeam.mockReturnValue(new Subject());
    await open();
    await fillValid();
    await submit();
    await submit();
    expect(createTeam).toHaveBeenCalledTimes(1);
    expect(el('btn-annuler-dialogue')?.querySelector('button')?.disabled).toBe(true);
    expect(document.body.querySelector('.p-dialog-close-button')).toBeNull();
  });

  it('annulation : « Annuler » ferme sans appeler l’API de création', async () => {
    await open();
    await type('champ-nom-equipe', 'Produit');
    el('btn-annuler-dialogue')?.querySelector('button')?.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.visible()).toBe(false);
    expect(createTeam).not.toHaveBeenCalled();
  });

  it('chaque ouverture repart d’un formulaire vide et relit les candidats', async () => {
    await open();
    await type('champ-nom-equipe', 'Produit');
    fixture.componentRef.setInput('visible', false);
    await fixture.whenStable();
    fixture.componentRef.setInput('visible', true);
    await fixture.whenStable();
    await fixture.whenStable();
    expect(input('champ-nom-equipe').value).toBe('');
    expect(listTeamManagerCandidates).toHaveBeenCalledTimes(2);
  });

  describe('modification', () => {
    beforeEach(() => {
      listTeamMemberCandidates.mockReturnValue(
        of([
          candidate(10, { currentTeam: { id: 4, name: 'Produit' } }),
          candidate(11, { active: false, currentTeam: { id: 4, name: 'Produit' } }),
          candidate(12, { currentTeam: { id: 5, name: 'Alpha' } }),
          candidate(13),
        ]),
      );
    });

    it('s’ouvre avec les valeurs de l’équipe : titre, nom, manager et membres dans l’ordre reçu du back (RG-05-13)', async () => {
      await open(existingTeam());
      expect(document.body.querySelector('.p-dialog-title')?.textContent).toContain('Modifier l’équipe');
      expect(input('champ-nom-equipe').value).toBe('Produit');
      expect(form().controls['managerId'].value).toBe(1);
      expect(form().controls['memberIds'].value).toEqual([10, 11]);
      expect(el('champ-manager')?.textContent).toContain('Manon1 Dupont1');
      const chips = Array.from(el('champ-membres')?.querySelectorAll('.p-chip-label') ?? []).map((c) => c.textContent?.trim());
      expect(chips).toEqual(['Paul10 Martin10', 'Paul11 Martin11 (inactif)']);
    });

    it('CA-05-10 : un membre inactif reste dans l’équipe et est signalé « Inactif » dans la liste et dans sa pastille', async () => {
      await open(existingTeam());
      el('champ-membres')?.click();
      await fixture.whenStable();
      expect(el('option-membre-11')?.textContent).toContain('Inactif');
      expect(el('option-membre-10')?.textContent).not.toContain('Inactif');
      expect(form().controls['memberIds'].value).toContain(11);
    });

    it('H10 : l’équipe en cours de modification n’est pas signalée sur ses propres membres, celle des autres l’est', async () => {
      await open(existingTeam());
      el('champ-membres')?.click();
      await fixture.whenStable();
      expect(el('option-membre-10')?.textContent).not.toContain('Déjà dans l’équipe');
      expect(el('option-membre-12')?.textContent).toContain('Déjà dans l’équipe Alpha');
    });

    it('CA-05-12 : changer le manager envoie le nouveau manager avec la version, ferme et recharge la liste', async () => {
      updateTeam.mockReturnValue(of(existingTeam({ manager: manager(2), version: 4 })));
      await open(existingTeam());
      await pick('champ-manager', 'option-manager-2');
      await submit();
      expect(updateTeam).toHaveBeenCalledWith(4, { name: 'Produit', managerId: 2, memberIds: [10, 11], version: 3 });
      expect(saved).toHaveBeenCalledWith(expect.objectContaining({ mode: 'update' }));
      expect(fixture.componentInstance.visible()).toBe(false);
      expect(listTeams).toHaveBeenCalled();
      expect(createTeam).not.toHaveBeenCalled();
    });

    it('envoie la liste complète des membres après ajout et retrait', async () => {
      updateTeam.mockReturnValue(of(existingTeam()));
      await open(existingTeam());
      await pick('champ-membres', 'option-membre-13');
      await pick('champ-membres', 'option-membre-10');
      await submit();
      expect(updateTeam).toHaveBeenCalledWith(4, { name: 'Produit', managerId: 1, memberIds: [11, 13], version: 3 });
    });

    it('le nom peut être changé ; un 409 RG-05-01 s’affiche sous le champ nom, dialogue ouvert', async () => {
      updateTeam.mockReturnValue(
        httpError(409, { status: 409, code: 'RG-05-01', detail: 'Ce nom d’équipe est déjà utilisé.', errors: [{ field: 'name', message: 'Ce nom d’équipe est déjà utilisé.' }] }),
      );
      await open(existingTeam());
      await type('champ-nom-equipe', 'ALPHA');
      await submit();
      expect(updateTeam).toHaveBeenCalledWith(4, expect.objectContaining({ name: 'ALPHA' }));
      expect(error('champ-nom-equipe')?.textContent).toContain('déjà utilisé');
      expect(fixture.componentInstance.visible()).toBe(true);
      expect(saved).not.toHaveBeenCalled();
    });

    it('CA-05-04 : en modification, le 409 RG-05-04 nommant l’autre équipe s’affiche sous les membres', async () => {
      updateTeam.mockReturnValue(
        httpError(409, { status: 409, code: 'RG-05-04', detail: 'Paul12 Martin12 est déjà membre de l’équipe « Alpha ».', errors: [{ field: 'memberIds', message: 'Paul12 Martin12 est déjà membre de l’équipe « Alpha ».' }] }),
      );
      await open(existingTeam());
      await pick('champ-membres', 'option-membre-12');
      await submit();
      expect(error('champ-membres')?.textContent).toContain('Alpha');
      expect(fixture.componentInstance.visible()).toBe(true);
    });

    it('conflit de version : 409 sans code ni errors[], le dialogue se ferme et la liste est rechargée', async () => {
      updateTeam.mockReturnValue(httpError(409, { status: 409, detail: 'Cette équipe a été modifiée par quelqu’un d’autre.' }));
      await open(existingTeam());
      await submit();
      expect(fixture.componentInstance.visible()).toBe(false);
      expect(conflict).toHaveBeenCalledTimes(1);
      expect(saved).not.toHaveBeenCalled();
    });

    it('une erreur 409 avec code (RG-05-xx) ne ferme pas le dialogue et n’est pas prise pour un conflit de version', async () => {
      updateTeam.mockReturnValue(httpError(409, { status: 409, code: 'RG-05-02', detail: 'Manager invalide.', errors: [{ field: 'managerId', message: 'Manager invalide.' }] }));
      await open(existingTeam());
      await submit();
      expect(conflict).not.toHaveBeenCalled();
      expect(error('champ-manager')?.textContent).toContain('Manager invalide');
    });

    it('rouvrir en création après une modification repart d’un formulaire vide', async () => {
      await open(existingTeam());
      fixture.componentRef.setInput('visible', false);
      fixture.componentRef.setInput('team', null);
      await fixture.whenStable();
      fixture.componentRef.setInput('visible', true);
      await fixture.whenStable();
      await fixture.whenStable();
      expect(input('champ-nom-equipe').value).toBe('');
      expect(form().controls['managerId'].value).toBeNull();
      expect(form().controls['memberIds'].value).toEqual([]);
    });
  });
});
