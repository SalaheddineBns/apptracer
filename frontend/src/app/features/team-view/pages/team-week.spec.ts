import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { ManagedTeamResponse, TeamViewService } from '../../../core/api/generated';
import { TeamWeekPage } from './team-week';

describe('TeamWeekPage (SFD-04 E1)', () => {
  const listMyManagedTeams = vi.fn();
  let harness: RouterTestingHarness;
  let root: HTMLElement;

  async function open(url = '/equipe'): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'equipe', component: TeamWeekPage }]),
        { provide: TeamViewService, useValue: { listMyManagedTeams } },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, TeamWeekPage);
    root = harness.routeNativeElement as HTMLElement;
    await harness.fixture.whenStable();
  }

  const cy = (name: string) => root.querySelector<HTMLElement>(`[data-cy="${name}"]`);

  beforeEach(() => {
    listMyManagedTeams.mockReset();
    TestBed.resetTestingModule();
  });

  it('affiche l’état de chargement tant que les équipes ne sont pas arrivées', async () => {
    listMyManagedTeams.mockReturnValue(new Subject<ManagedTeamResponse[]>());
    await open();
    expect(cy('etat-chargement')).not.toBeNull();
    expect(cy('titre-equipe')?.textContent).toContain('Mon équipe');
    expect(cy('message-aucune-equipe')).toBeNull();
  });

  it('CA-04-09 : un MANAGER sans équipe voit un message indiquant qu’aucune équipe ne lui est rattachée', async () => {
    listMyManagedTeams.mockReturnValue(of([]));
    await open();
    expect(cy('message-aucune-equipe')?.textContent).toContain('Aucune équipe ne vous est rattachée.');
    expect(cy('etat-chargement')).toBeNull();
    expect(cy('etat-erreur')).toBeNull();
  });

  it('affiche l’état d’erreur avec « Réessayer », qui recharge les équipes', async () => {
    listMyManagedTeams.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Panne serveur' } })),
    );
    await open();
    expect(cy('etat-erreur')?.textContent).toContain('Panne serveur');
    listMyManagedTeams.mockReturnValue(of([]));
    cy('btn-reessayer')?.querySelector('button')?.click();
    await harness.fixture.whenStable();
    expect(cy('etat-erreur')).toBeNull();
    expect(cy('message-aucune-equipe')).not.toBeNull();
  });
});
