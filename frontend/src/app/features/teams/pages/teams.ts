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

/** SFD-05 E1 Teams (ADMIN). */
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
  /** Team being edited; `null` for a creation. */
  protected readonly editing = signal<TeamResponse | null>(null);

  constructor() {
    // A single request per opening of the screen.
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

  /** CA-05-01: '2 members'; singular at 0 and 1 in French, singular at 1 only in English (RG-08-08). */
  protected memberCountLabel(count: number): string {
    return this.i18n.plural('teams.memberCount', count);
  }

  /** E3, RG-05-09: confirmation quoting the team's name and the number of members concerned. */
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
            // The row is gone: the focus moves to the page's main action rather than falling onto <body>.
            setTimeout(() => document.querySelector<HTMLElement>('[data-cy="btn-nouvelle-equipe"] button')?.focus());
          },
          // Already deleted by someone else (404, message shown by the interceptor): the list is re-read.
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
