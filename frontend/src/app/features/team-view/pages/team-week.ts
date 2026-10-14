import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { TeamViewStore } from '../data/team-view.store';

/** SFD-04 E1 Mon équipe (MANAGER). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyState, ErrorState, LoadingState],
  selector: 'app-team-week',
  templateUrl: './team-week.html',
})
export class TeamWeekPage {
  protected readonly store = inject(TeamViewStore);

  constructor() {
    // Une seule requête par ouverture de l'écran.
    this.store.loadTeams();
  }
}
