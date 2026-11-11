import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import {
  Granularity,
  MemberWeeklyTimeEntriesResponse,
  TagColor,
  TeamViewService,
  TimeEntryResponse,
} from '../../../core/api/generated';
import { MemberJournalPage } from './member-journal';
import { TeamWeekPage } from './team-week';

const meeting = (over: Partial<TimeEntryResponse> = {}): TimeEntryResponse => ({
  id: 1,
  granularity: Granularity.Hour,
  startDate: '2026-10-05',
  startTime: '09:00',
  endTime: '10:30',
  activityType: { code: 'REUNION', label: 'Réunion', color: TagColor.Warn, active: true },
  comment: 'Point hebdomadaire',
  durationMinutes: 90,
  version: 0,
  ...over,
});
const journal = (
  entries: TimeEntryResponse[],
  totalMinutes: number,
  active = true,
): MemberWeeklyTimeEntriesResponse => ({
  team: { id: 7, name: 'Alpha' },
  member: { id: 10, firstName: 'Ada', lastName: 'Durand', active },
  week: { weekStart: '2026-10-05', weekEnd: '2026-10-11', totalMinutes, days: [], entries },
});

describe('MemberJournalPage (SFD-04 E2)', () => {
  const listTeamMemberTimeEntries = vi.fn();
  const listMyManagedTeams = vi.fn();
  const getTeamWeekSummary = vi.fn();
  let harness: RouterTestingHarness;
  let root: HTMLElement;

  async function open(url = '/team/members/10?week=2026-10-05'): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'team', component: TeamWeekPage },
          { path: 'team/members/:userId', component: MemberJournalPage },
        ]),
        {
          provide: TeamViewService,
          useValue: { listTeamMemberTimeEntries, listMyManagedTeams, getTeamWeekSummary },
        },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, MemberJournalPage);
    root = harness.routeNativeElement as HTMLElement;
    await harness.fixture.whenStable();
  }

  const cy = (name: string) => root.querySelector<HTMLElement>(`[data-cy="${name}"]`);
  const click = async (name: string) => {
    (cy(name)?.querySelector('button') ?? cy(name))?.click();
    await harness.fixture.whenStable();
  };
  const url = () => TestBed.inject(Router).url;

  beforeEach(() => {
    listTeamMemberTimeEntries.mockReset();
    listMyManagedTeams.mockReset();
    getTeamWeekSummary.mockReset();
    TestBed.resetTestingModule();
  });

  it('affiche l’état de chargement tant que le journal n’est pas arrivé', async () => {
    listTeamMemberTimeEntries.mockReturnValue(new Subject());
    await open();
    expect(cy('etat-chargement')).not.toBeNull();
    expect(cy('journal-semaine')).toBeNull();
    expect(cy('btn-retour-equipe')).not.toBeNull();
  });

  it('affiche l’état d’erreur avec « Réessayer », qui recharge le même membre et la même semaine', async () => {
    listTeamMemberTimeEntries.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })),
    );
    await open();
    expect(cy('etat-erreur')?.textContent).toContain('Panne serveur');
    expect(cy('message-membre-interdit')).toBeNull();
    listTeamMemberTimeEntries.mockReturnValue(of(journal([meeting()], 90)));
    await click('btn-reessayer');
    expect(cy('etat-erreur')).toBeNull();
    expect(cy('journal-semaine')).not.toBeNull();
    expect(listTeamMemberTimeEntries).toHaveBeenLastCalledWith(10, '2026-10-05', 'body', false, expect.anything());
  });

  it('affiche l’état vide, sans aucune action, quand la semaine n’a aucune saisie', async () => {
    listTeamMemberTimeEntries.mockReturnValue(of(journal([], 0)));
    await open();
    expect(cy('etat-vide')?.textContent).toContain('Aucune activité cette semaine.');
    expect(cy('etat-vide')?.querySelector('button')).toBeNull();
    expect(cy('journal-semaine')).toBeNull();
    expect(cy('jour-2026-10-05')).toBeNull();
  });

  it('CA-04-02 : le MANAGER voit les saisies de la semaine du membre, avec commentaire, et aucun bouton de création, de modification ni de suppression', async () => {
    listTeamMemberTimeEntries.mockReturnValue(
      of(
        journal(
          [
            meeting(),
            meeting({ id: 2, startDate: '2026-10-06', startTime: '14:00', endTime: '15:00', durationMinutes: 60, comment: null }),
          ],
          150,
        ),
      ),
    );
    await open();
    expect(listTeamMemberTimeEntries).toHaveBeenCalledWith(10, '2026-10-05', 'body', false, expect.anything());
    expect(cy('titre-journal-membre')?.textContent).toContain('Journal : Ada Durand');
    expect(cy('saisie-1')?.textContent).toContain('Réunion');
    expect(cy('saisie-1')?.textContent).toContain('1 h 30');
    expect(cy('saisie-1')?.querySelector('[data-cy="saisie-commentaire"]')?.textContent).toContain('Point hebdomadaire');
    expect(cy('saisie-2')?.querySelector('[data-cy="saisie-commentaire"]')).toBeNull();
    expect(cy('total-semaine')?.textContent).toContain('2,5 h');

    expect(cy('btn-nouvelle-saisie')).toBeNull();
    expect(cy('btn-nouvelle-saisie-vide')).toBeNull();
    expect(root.querySelector('[data-cy^="btn-ajouter-saisie-"]')).toBeNull();
    expect(root.querySelector('[data-cy^="btn-modifier-saisie-"]')).toBeNull();
    expect(root.querySelector('[data-cy^="btn-supprimer-saisie-"]')).toBeNull();
    // The screen's only buttons are the way back to the team and the week navigation.
    const buttons = Array.from(root.querySelectorAll('button')).map((b) => b.closest('[data-cy]')?.getAttribute('data-cy'));
    expect(buttons.every((name) => ['btn-retour-equipe', 'btn-semaine-precedente', 'btn-semaine-suivante'].includes(name ?? ''))).toBe(true);
  });

  it('SFD-07 : le journal d’un membre n’affiche pas le total de chaque jour', async () => {
    const data = journal([meeting()], 90);
    data.week.days = [{ date: '2026-10-05', minutes: 90 }];
    listTeamMemberTimeEntries.mockReturnValue(of(data));
    await open();
    expect(cy('jour-2026-10-05')).not.toBeNull();
    expect(root.querySelector('[data-cy^="total-jour-"]')).toBeNull();
    expect(root.textContent).not.toContain('Total du lundi');
  });

  it('CA-04-12 : un membre dont le compte est désactivé affiche la mention « Inactif » et ses saisies restent consultables', async () => {
    listTeamMemberTimeEntries.mockReturnValue(of(journal([meeting()], 90, false)));
    await open();
    expect(cy('tag-inactif-10')?.textContent).toContain('Inactif');
    expect(cy('saisie-1')).not.toBeNull();
  });

  it('pas de mention « Inactif » pour un membre actif', async () => {
    listTeamMemberTimeEntries.mockReturnValue(of(journal([meeting()], 90)));
    await open();
    expect(cy('tag-inactif-10')).toBeNull();
  });

  it('RG-04-04 : « Semaine précédente » charge la semaine précédente du même membre et la met dans l’URL', async () => {
    listTeamMemberTimeEntries.mockReturnValue(of(journal([], 0)));
    await open();
    await click('btn-semaine-precedente');
    expect(url()).toBe('/team/members/10?week=2026-09-28');
    expect(listTeamMemberTimeEntries).toHaveBeenLastCalledWith(10, '2026-09-28', 'body', false, expect.anything());
    expect(cy('libelle-semaine')?.textContent).toContain('28/09/2026');
  });

  it('SFD-04 E2 : « Retour à Mon équipe » revient à E1 en conservant la semaine et l’équipe du membre', async () => {
    listTeamMemberTimeEntries.mockReturnValue(of(journal([meeting()], 90)));
    listMyManagedTeams.mockReturnValue(of([{ id: 3, name: 'Aaa' }, { id: 7, name: 'Alpha' }]));
    getTeamWeekSummary.mockReturnValue(
      of({ team: { id: 7, name: 'Alpha' }, weekStart: '2026-10-12', weekEnd: '2026-10-18', members: [] }),
    );
    await open('/team/members/10?week=2026-10-12');
    await click('btn-retour-equipe');
    await harness.fixture.whenStable();
    expect(url()).toBe('/team?team=7&week=2026-10-12');
    expect(getTeamWeekSummary).toHaveBeenCalledWith(7, '2026-10-12', 'body', false, expect.anything());
  });

  it('SFD-04 E2 : tant que le journal n’est pas chargé, « Retour à Mon équipe » garde l’équipe portée par l’URL', async () => {
    listTeamMemberTimeEntries.mockReturnValue(new Subject());
    listMyManagedTeams.mockReturnValue(of([{ id: 3, name: 'Aaa' }, { id: 7, name: 'Alpha' }]));
    getTeamWeekSummary.mockReturnValue(
      of({ team: { id: 7, name: 'Alpha' }, weekStart: '2026-10-12', weekEnd: '2026-10-18', members: [] }),
    );
    await open('/team/members/10?team=7&week=2026-10-12');
    await click('btn-retour-equipe');
    await harness.fixture.whenStable();
    expect(url()).toBe('/team?team=7&week=2026-10-12');
  });

  it('RG-04-02 : un compte hors périmètre (403, membre retiré) mène à un message clair, sans « Réessayer », et le retour reste possible', async () => {
    listTeamMemberTimeEntries.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403, error: { detail: 'Accès refusé', code: 'RG-04-02' } })),
    );
    await open();
    expect(cy('message-membre-interdit')?.textContent).toContain('ne fait pas partie de vos équipes');
    expect(cy('btn-reessayer')).toBeNull();
    expect(cy('journal-semaine')).toBeNull();
    expect(cy('btn-retour-equipe')).not.toBeNull();
  });

  it('un identifiant de membre invalide affiche le même message, sans appel API', async () => {
    await open('/team/members/abc?week=2026-10-05');
    expect(cy('message-membre-interdit')).not.toBeNull();
    expect(listTeamMemberTimeEntries).not.toHaveBeenCalled();
  });
});
