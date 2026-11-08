import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { TeamAccountSummary, TeamResponse } from '../../../core/api/generated';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { TeamDialog, TeamSaved } from '../components/team-dialog';
import { TeamsStore } from '../data/teams.store';

/** SFD-05 E1 Équipes (ADMIN). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TableModule, ButtonModule, EmptyState, ErrorState, LoadingState, TeamDialog, TPipe],
  selector: 'app-teams',
  templateUrl: './teams.html',
})
export class TeamsPage {
  protected readonly store = inject(TeamsStore);
  private readonly confirmation = inject(ConfirmationService);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18n);

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
    const name = event.team.name;
    this.messages.add({
      severity: 'success',
      summary: this.i18n.t(event.mode === 'create' ? 'teams.toast.created' : 'teams.toast.updated'),
      detail: this.i18n.t(event.mode === 'create' ? 'teams.toast.createdDetail' : 'teams.toast.updatedDetail', { name }),
      life: 3000,
    });
  }

  protected fullName(account: TeamAccountSummary): string {
    return `${account.firstName} ${account.lastName}`;
  }

  /** CA-05-01 : « 2 membres » ; singulier à 0 et 1 en français, singulier à 1 seulement en anglais (RG-08-08). */
  protected memberCountLabel(count: number): string {
    return this.i18n.plural('teams.memberCount', count);
  }

  /** E3, RG-05-09 : confirmation citant le nom de l'équipe et le nombre de membres concernés. */
  protected confirmRemove(team: TeamResponse): void {
    this.confirmation.confirm({
      header: this.i18n.t('teams.delete.header'),
      message: this.i18n.t('teams.delete.message', { name: team.name, consequence: this.consequence(team.memberCount) }),
      acceptLabel: this.i18n.t('common.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () =>
        this.store.remove(team.id).subscribe({
          next: () => {
            this.messages.add({ severity: 'success', summary: this.i18n.t('teams.delete.deleted'), detail: this.i18n.t('teams.delete.deletedDetail', { name: team.name }), life: 3000 });
            // La ligne a disparu : le focus passe à l'action principale de la page plutôt que de tomber sur <body>.
            setTimeout(() => document.querySelector<HTMLElement>('[data-cy="btn-nouvelle-equipe"] button')?.focus());
          },
          // Déjà supprimée par quelqu'un d'autre (404, message affiché par l'intercepteur) : la liste est relue.
          error: () => this.store.load(),
        }),
    });
  }

  private consequence(memberCount: number): string {
    if (memberCount === 0) {
      return this.i18n.t('teams.delete.noMember');
    }
    return memberCount === 1
      ? this.i18n.t('teams.delete.oneMember')
      : this.i18n.t('teams.delete.manyMembers', { count: memberCount });
  }
}
