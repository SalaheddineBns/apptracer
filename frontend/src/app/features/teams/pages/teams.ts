import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TeamAccountSummary, TeamResponse } from '../../../core/api/generated';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { TeamDialog, TeamSaved } from '../components/team-dialog';
import { TeamsStore } from '../data/teams.store';

/** SFD-05 E1 Équipes (ADMIN). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TableModule, ButtonModule, EmptyState, ErrorState, LoadingState, TeamDialog],
  selector: 'app-teams',
  templateUrl: './teams.html',
})
export class TeamsPage {
  protected readonly store = inject(TeamsStore);
  private readonly messages = inject(MessageService);

  protected readonly dialogVisible = signal(false);
  /** Équipe en cours de modification ; `null` pour une création. */
  protected readonly editing = signal<TeamResponse | null>(null);

  constructor() {
    // Une seule requête par ouverture de l'écran.
    this.store.load();
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.dialogVisible.set(true);
  }

  protected openEdit(team: TeamResponse): void {
    this.editing.set(team);
    this.dialogVisible.set(true);
  }

  protected onSaved(event: TeamSaved): void {
    this.messages.add({
      severity: 'success',
      summary: event.mode === 'create' ? 'Équipe créée' : 'Équipe mise à jour',
      detail: event.mode === 'create' ? `L’équipe « ${event.team.name} » est créée.` : `L’équipe « ${event.team.name} » est modifiée.`,
      life: 3000,
    });
  }

  protected fullName(account: TeamAccountSummary): string {
    return `${account.firstName} ${account.lastName}`;
  }

  /** CA-05-01 : « 2 membres » ; singulier à 0 et 1 (usage français). */
  protected memberCountLabel(count: number): string {
    return count > 1 ? `${count} membres` : `${count} membre`;
  }
}
