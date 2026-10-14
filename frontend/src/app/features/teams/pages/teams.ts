import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TeamAccountSummary } from '../../../core/api/generated';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { TeamsStore } from '../data/teams.store';

/** SFD-05 E1 Équipes (ADMIN). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TableModule, ButtonModule, EmptyState, ErrorState, LoadingState],
  selector: 'app-teams',
  templateUrl: './teams.html',
})
export class TeamsPage {
  protected readonly store = inject(TeamsStore);

  constructor() {
    // Une seule requête par ouverture de l'écran.
    this.store.load();
  }

  protected openCreate(): void {
    // Le dialogue E2 est branché par T-05-10.
  }

  protected fullName(account: TeamAccountSummary): string {
    return `${account.firstName} ${account.lastName}`;
  }

  /** CA-05-01 : « 2 membres » ; singulier à 0 et 1 (usage français). */
  protected memberCountLabel(count: number): string {
    return count > 1 ? `${count} membres` : `${count} membre`;
  }
}
