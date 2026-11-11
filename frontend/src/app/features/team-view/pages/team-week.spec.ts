import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import {
  ManagedTeamResponse,
  MemberWeekSummary,
  TeamViewService,
  TeamWeekSummaryResponse,
} from '../../../core/api/generated';
import { weekDays } from '../../../shared/dates/week';
import { TeamWeekPage } from './team-week';

const alpha: ManagedTeamResponse = { id: 1, name: 'Alpha' };
const beta: ManagedTeamResponse = { id: 2, name: 'Beta' };

/** Table row as returned by the API: `minutes` per day, Monday to Sunday. */
const member = (
  id: number,
  lastName: string,
  minutes: number[],
  totalMinutes: number,
  active = true,
  weekStart = '2026-10-05',
): MemberWeekSummary => ({
  member: { id, firstName: 'Prénom' + id, lastName, active },
  days: weekDays(weekStart).map((date, index) => ({ date, minutes: minutes[index] ?? 0 })),
  totalMinutes,
});
const summary = (
  team: ManagedTeamResponse,
  members: MemberWeekSummary[],
  weekStart = '2026-10-05',
): TeamWeekSummaryResponse => ({ team, weekStart, weekEnd: '2026-10-11', members });

describe('TeamWeekPage (SFD-04 E1)', () => {
  const listMyManagedTeams = vi.fn();
  const getTeamWeekSummary = vi.fn();
  let harness: RouterTestingHarness;
  let root: HTMLElement;

  async function open(url = '/team?week=2026-10-05'): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'team', component: TeamWeekPage }]),
        { provide: TeamViewService, useValue: { listMyManagedTeams, getTeamWeekSummary } },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, TeamWeekPage);
    root = harness.routeNativeElement as HTMLElement;
    await harness.fixture.whenStable();
  }

  const cy = (name: string) => root.querySelector<HTMLElement>(`[data-cy="${name}"]`);
  const body = (name: string) => document.body.querySelector<HTMLElement>(`[data-cy="${name}"]`);
  const click = async (name: string) => {
    (cy(name)?.querySelector('button') ?? cy(name))?.click();
    await harness.fixture.whenStable();
  };
  const url = () => TestBed.inject(Router).url;

  beforeEach(() => {
    // jsdom n'a pas matchMedia, utilisé par les panneaux déroulants de PrimeNG (p-select), ni scrollIntoView.
    vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn() }));
    HTMLElement.prototype.scrollIntoView = vi.fn();
    // jsdom n'a pas ResizeObserver, utilisé par la colonne figée (pFrozenColumn).
    vi.stubGlobal('ResizeObserver', class { observe = vi.fn(); unobserve = vi.fn(); disconnect = vi.fn(); });
    listMyManagedTeams.mockReset();
    getTeamWeekSummary.mockReset();
    TestBed.resetTestingModule();
    document.body.querySelectorAll('.p-overlay').forEach((n) => n.remove());
  });

  it('affiche l’état de chargement tant que les équipes ne sont pas arrivées', async () => {
    listMyManagedTeams.mockReturnValue(new Subject<ManagedTeamResponse[]>());
    await open();
    expect(cy('etat-chargement')).not.toBeNull();
    expect(cy('titre-equipe')?.textContent).toContain('Mon équipe');
    expect(cy('message-aucune-equipe')).toBeNull();
    expect(getTeamWeekSummary).not.toHaveBeenCalled();
  });

  it('CA-04-09 : un MANAGER sans équipe voit un message indiquant qu’aucune équipe ne lui est rattachée', async () => {
    listMyManagedTeams.mockReturnValue(of([]));
    await open();
    expect(cy('message-aucune-equipe')?.textContent).toContain('Aucune équipe ne vous est rattachée.');
    expect(cy('etat-chargement')).toBeNull();
    expect(cy('etat-erreur')).toBeNull();
    expect(cy('table-equipe-semaine')).toBeNull();
    expect(getTeamWeekSummary).not.toHaveBeenCalled();
  });

  it('affiche l’état d’erreur des équipes avec « Réessayer », qui recharge les équipes', async () => {
    listMyManagedTeams.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })),
    );
    await open();
    expect(cy('etat-erreur')?.textContent).toContain('Panne serveur');
    listMyManagedTeams.mockReturnValue(of([]));
    await click('btn-reessayer');
    expect(cy('etat-erreur')).toBeNull();
    expect(cy('message-aucune-equipe')).not.toBeNull();
  });

  it('affiche l’état de chargement du tableau tant que la semaine n’est pas arrivée', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha]));
    getTeamWeekSummary.mockReturnValue(new Subject<TeamWeekSummaryResponse>());
    await open();
    expect(cy('etat-chargement')).not.toBeNull();
    expect(cy('table-equipe-semaine')).toBeNull();
    expect(cy('navigation-semaine')).not.toBeNull();
  });

  it('le sélecteur d’équipe a une largeur fixée par la classe posée sur son hôte (w-64 max-w-full)', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha, beta]));
    getTeamWeekSummary.mockReturnValue(of(summary(alpha, [])));
    await open();
    const select = cy('select-equipe') as HTMLElement;
    expect(select.classList.contains('w-64')).toBe(true);
    expect(select.classList.contains('max-w-full')).toBe(true);
  });

  it('pendant le chargement des équipes, un squelette de la navigation de semaine réserve sa place', async () => {
    listMyManagedTeams.mockReturnValue(new Subject<ManagedTeamResponse[]>());
    await open();
    expect(cy('squelette-navigation-semaine')).not.toBeNull();
    expect(cy('navigation-semaine')).toBeNull();
  });

  it('affiche l’état d’erreur de la semaine avec « Réessayer », qui recharge la même équipe et la même semaine', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha]));
    getTeamWeekSummary.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })),
    );
    await open();
    expect(cy('etat-erreur')?.textContent).toContain('Panne serveur');
    expect(cy('message-equipe-interdite')).toBeNull();
    getTeamWeekSummary.mockReturnValue(of(summary(alpha, [member(10, 'Durand', [], 0)])));
    await click('btn-reessayer');
    expect(cy('etat-erreur')).toBeNull();
    expect(cy('table-equipe-semaine')).not.toBeNull();
    expect(getTeamWeekSummary).toHaveBeenLastCalledWith(1, '2026-10-05', 'body', false, expect.anything());
  });

  it('CA-04-01 : deux membres ayant saisi 90 min et 8 h sont affichés avec leurs totaux 1,5 h et 8 h', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha]));
    getTeamWeekSummary.mockReturnValue(
      of(summary(alpha, [member(10, 'Durand', [90], 90), member(11, 'Martin', [0, 480], 480)])),
    );
    await open();
    expect(cy('ligne-membre-10')?.textContent).toContain('Prénom10 Durand');
    expect(cy('ligne-membre-11')?.textContent).toContain('Prénom11 Martin');
    expect(cy('total-membre-10')?.textContent?.trim()).toBe('1,5 h');
    expect(cy('total-membre-11')?.textContent?.trim()).toBe('8 h');
    expect(cy('duree-membre-10-2026-10-05')?.textContent?.trim()).toBe('1,5 h');
    expect(cy('duree-membre-11-2026-10-06')?.textContent?.trim()).toBe('8 h');
    expect(getTeamWeekSummary).toHaveBeenCalledWith(1, '2026-10-05', 'body', false, expect.anything());
  });

  it('le tableau a une colonne par jour du lundi au dimanche, et la durée d’un jour sans saisie est lisible comme 0 h', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha]));
    getTeamWeekSummary.mockReturnValue(of(summary(alpha, [member(10, 'Durand', [90], 90)])));
    await open();
    const headers = Array.from(root.querySelectorAll('[data-cy^="colonne-jour-"]')).map((h) => h.getAttribute('data-cy'));
    expect(headers).toEqual(weekDays('2026-10-05').map((d) => 'colonne-jour-' + d));
    expect(cy('colonne-jour-2026-10-05')?.textContent).toContain('lundi');
    expect(cy('colonne-jour-2026-10-05')?.textContent).toContain('05/10');
    expect(cy('duree-membre-10-2026-10-07')?.textContent).toContain('0 h');
    expect(cy('table-equipe-semaine')?.classList.contains('text-sm')).toBe(true);
  });

  it('RG-04-09 : l’ordre des membres reçu de l’API est conservé', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha]));
    getTeamWeekSummary.mockReturnValue(
      of(summary(alpha, [member(12, 'Zola', [], 0), member(10, 'Adam', [], 0), member(11, 'Moreau', [], 0)])),
    );
    await open();
    const rows = Array.from(root.querySelectorAll('[data-cy^="ligne-membre-"]')).map((r) => r.getAttribute('data-cy'));
    expect(rows).toEqual(['ligne-membre-12', 'ligne-membre-10', 'ligne-membre-11']);
  });

  it('CA-04-07 : le total d’un membre ayant une PLAGE à cheval est celui de l’API (16 h), pas une somme refaite côté écran', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha]));
    // Jeudi 08 et vendredi 09/10 : 8 h chacun ; samedi et dimanche non comptés (RG-04-05). L'API renvoie 960 min.
    getTeamWeekSummary.mockReturnValue(of(summary(alpha, [member(10, 'Durand', [0, 0, 0, 480, 480], 960)])));
    await open();
    expect(cy('total-membre-10')?.textContent?.trim()).toBe('16 h');
    expect(cy('duree-membre-10-2026-10-08')?.textContent?.trim()).toBe('8 h');
    expect(cy('duree-membre-10-2026-10-09')?.textContent?.trim()).toBe('8 h');
    expect(cy('duree-membre-10-2026-10-10')?.textContent).toContain('0 h');
  });

  it('CA-04-08 : seul le total fourni par l’API est affiché, même s’il diffère de la somme des jours', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha]));
    getTeamWeekSummary.mockReturnValue(of(summary(alpha, [member(10, 'Durand', [480, 480], 480)])));
    await open();
    expect(cy('total-membre-10')?.textContent?.trim()).toBe('8 h');
  });

  it('CA-04-12 : un membre dont le compte est désactivé reste affiché avec la mention « Inactif », pas un membre actif', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha]));
    getTeamWeekSummary.mockReturnValue(
      of(summary(alpha, [member(10, 'Durand', [90], 90, false), member(11, 'Martin', [], 0, true)])),
    );
    await open();
    expect(cy('ligne-membre-10')).not.toBeNull();
    expect(cy('tag-inactif-10')?.textContent).toContain('Inactif');
    expect(cy('tag-inactif-11')).toBeNull();
  });

  it('CA-04-02 : le nom du membre est un lien vers son journal, avec la semaine affichée, accessible au clavier', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha]));
    getTeamWeekSummary.mockReturnValue(of(summary(alpha, [member(10, 'Durand', [90], 90)])));
    await open();
    const link = cy('lien-membre-10') as HTMLAnchorElement;
    expect(link.tagName).toBe('A');
    expect(link.getAttribute('href')).toBe('/team/members/10?team=1&week=2026-10-05');
    expect(link.tabIndex).toBeGreaterThanOrEqual(0);
  });

  it('une équipe sans membre affiche « Cette équipe n’a aucun membre. »', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha]));
    getTeamWeekSummary.mockReturnValue(of(summary(alpha, [])));
    await open();
    expect(cy('etat-vide')?.textContent).toContain('Cette équipe n\'a aucun membre.');
    expect(cy('table-equipe-semaine')).toBeNull();
  });

  it('RG-04-08 : avec une seule équipe, le sélecteur n’est pas affiché', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha]));
    getTeamWeekSummary.mockReturnValue(of(summary(alpha, [])));
    await open();
    expect(cy('select-equipe')).toBeNull();
  });

  it('RG-04-08 : avec plusieurs équipes, la première de la liste (ordre alphabétique de l’API) est affichée par défaut', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha, beta]));
    getTeamWeekSummary.mockReturnValue(of(summary(alpha, [member(10, 'Durand', [90], 90)])));
    await open('/team');
    expect(cy('select-equipe')).not.toBeNull();
    expect(cy('select-equipe')?.textContent).toContain('Alpha');
    expect(getTeamWeekSummary.mock.calls[0][0]).toBe(1);
  });

  it('CA-04-10 : changer d’équipe dans le sélecteur affiche les membres et totaux de l’équipe choisie, pour la même semaine', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha, beta]));
    getTeamWeekSummary.mockImplementation((teamId: number) =>
      of(
        teamId === 1
          ? summary(alpha, [member(10, 'Durand', [90], 90)], '2026-10-12')
          : summary(beta, [member(20, 'Leroy', [480], 480)], '2026-10-12'),
      ),
    );
    await open('/team?team=1&week=2026-10-12');
    expect(cy('ligne-membre-10')).not.toBeNull();

    (cy('select-equipe') as HTMLElement).click();
    await harness.fixture.whenStable();
    body('option-equipe-2')?.click();
    await harness.fixture.whenStable();

    expect(url()).toBe('/team?team=2&week=2026-10-12');
    expect(getTeamWeekSummary).toHaveBeenLastCalledWith(2, '2026-10-12', 'body', false, expect.anything());
    expect(cy('ligne-membre-10')).toBeNull();
    expect(cy('total-membre-20')?.textContent?.trim()).toBe('8 h');
  });

  it('RG-04-04 : « Semaine suivante » charge la semaine suivante de la même équipe et la met dans l’URL', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha, beta]));
    getTeamWeekSummary.mockReturnValue(of(summary(beta, [])));
    await open('/team?team=2&week=2026-10-05');
    await click('btn-semaine-suivante');
    expect(url()).toBe('/team?team=2&week=2026-10-12');
    expect(getTeamWeekSummary).toHaveBeenLastCalledWith(2, '2026-10-12', 'body', false, expect.anything());
    expect(cy('libelle-semaine')?.textContent).toContain('12/10/2026');
  });

  it('une date quelconque de l’URL est ramenée au lundi de sa semaine', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha]));
    getTeamWeekSummary.mockReturnValue(of(summary(alpha, [])));
    await open('/team?week=2026-10-08');
    expect(getTeamWeekSummary.mock.calls[0][1]).toBe('2026-10-05');
  });

  it('RG-04-02 : une équipe hors périmètre (403) mène à un message clair avec « Afficher mes équipes », sans « Réessayer »', async () => {
    listMyManagedTeams.mockReturnValue(of([alpha, beta]));
    getTeamWeekSummary.mockImplementation((teamId: number) =>
      teamId === 99
        ? throwError(() => new HttpErrorResponse({ status: 403, error: { detail: 'Accès refusé', code: 'RG-04-02' } }))
        : of(summary(alpha, [member(10, 'Durand', [90], 90)])),
    );
    await open('/team?team=99&week=2026-10-05');
    expect(cy('message-equipe-interdite')?.textContent).toContain('ne fait pas partie');
    expect(cy('btn-reessayer')).toBeNull();
    expect(cy('table-equipe-semaine')).toBeNull();
    expect(cy('navigation-semaine')).toBeNull();

    (cy('btn-afficher-mes-equipes')?.querySelector('button') as HTMLElement).focus();
    await click('btn-afficher-mes-equipes');
    expect(document.activeElement).toBe(cy('titre-equipe'));
    expect(url()).toBe('/team?week=2026-10-05');
    expect(cy('message-equipe-interdite')).toBeNull();
    expect(cy('ligne-membre-10')).not.toBeNull();
    expect(cy('navigation-semaine')).not.toBeNull();
  });
});
