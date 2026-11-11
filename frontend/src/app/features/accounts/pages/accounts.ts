import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { Role, UserResponse } from '../../../core/api/generated';
import { AuthStore } from '../../../core/auth/auth.store';
import { ROLE_LABEL_KEYS } from '../../../core/auth/roles';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { FocusRestorer } from '../../../shared/focus-restorer';
import { JobTitlesStore } from '../../job-titles/data/job-titles.store';
import { AccountDialog,AccountSaved } from '../components/account-dialog';
import { AccountsStore, isAccountSortField } from '../data/accounts.store';

const ROLE_SEVERITIES: Record<Role, 'secondary' | 'info' | 'contrast'> = {
  [Role.User]: 'secondary',
  [Role.Manager]: 'info',
  [Role.Admin]: 'contrast',
};

/** SFD-03 E4 Comptes (ADMIN). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TableModule, TagModule, ButtonModule, EmptyState, ErrorState, LoadingState, AccountDialog, TPipe],
  selector: 'app-accounts',
  templateUrl: './accounts.html',
})
export class AccountsPage {
  protected readonly store = inject(AccountsStore);
  private readonly auth = inject(AuthStore);
  private readonly confirmation = inject(ConfirmationService);
  private readonly messages = inject(MessageService);
  private readonly focus = inject(FocusRestorer);
  private readonly i18n = inject(I18n);

  protected readonly first = computed(() => this.store.page() * this.store.size());
  protected readonly rowsPerPage = [10, 20, 50];

  protected readonly dialogVisible = signal(false);
  /** Account being edited; `null` for a creation. */
  protected readonly editing = signal<UserResponse | null>(null);

  constructor() {
    this.store.load();
    // RG-09-06: the job titles (inactive ones included, for the ADMIN) feed the dialog's dropdown.
    inject(JobTitlesStore).load();
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.dialogVisible.set(true);
  }

  protected openEdit(user: UserResponse): void {
    this.editing.set(user);
    this.dialogVisible.set(true);
  }

  protected onSaved(event: AccountSaved): void {
    const name = this.fullName(event.user);
    this.notify(
      this.i18n.t(event.mode === 'create' ? 'accounts.toast.created' : 'accounts.toast.updated'),
      this.i18n.t(event.mode === 'create' ? 'accounts.toast.createdDetail' : 'accounts.toast.updatedDetail', { name }),
    );
  }

  protected roleLabel(role: Role): string {
    return this.i18n.t(ROLE_LABEL_KEYS[role]);
  }

  protected roleSeverity(role: Role): 'secondary' | 'info' | 'contrast' {
    return ROLE_SEVERITIES[role];
  }

  protected isSelf(user: UserResponse): boolean {
    return user.id === this.auth.user()?.id;
  }

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    const rows = event.rows ?? this.store.size();
    const first = event.first ?? 0;
    const field = isAccountSortField(event.sortField) ? event.sortField : this.store.sortField();
    const order = event.sortOrder === -1 ? -1 : event.sortOrder === 1 ? 1 : this.store.sortOrder();
    this.store.query(Math.floor(first / rows), rows, field, order);
  }

  protected fullName(user: UserResponse): string {
    return `${user.firstName} ${user.lastName}`;
  }

  /** Deactivation: confirmation first (RG-03-14: its sessions are revoked). */
  protected confirmDeactivate(user: UserResponse): void {
    this.confirmation.confirm({
      header: this.i18n.t('accounts.deactivate.header'),
      message: this.i18n.t('accounts.deactivate.message', { name: this.fullName(user), email: user.email }),
      acceptLabel: this.i18n.t('common.deactivate'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () =>
        this.store.deactivate(user.id).subscribe({
          next: () => this.notify(this.i18n.t('accounts.toast.updated'), this.i18n.t('accounts.toast.deactivatedDetail', { name: this.fullName(user) })),
          // RG-05-10 (a team's manager, 409 naming the team), RG-03-13: the refusal is shown as a toast by the error
          // interceptor, the row stays unchanged (Active) and its button is available again.
          error: () => undefined,
        }),
    });
  }

  /** Reactivation: reversible, without confirmation. */
  protected reactivate(user: UserResponse): void {
    // The button is disabled during the call, then replaced by Deactivate: the focus follows it.
    const anchor = this.focus.capture();
    this.store.reactivate(user.id)
      .pipe(finalize(() => this.focus.restore(anchor)))
      .subscribe(() => this.notify(this.i18n.t('accounts.toast.updated'), this.i18n.t('accounts.toast.reactivatedDetail', { name: this.fullName(user) })));
  }

  private notify(summary: string, detail: string): void {
    this.messages.add({ severity: 'success', summary, detail, life: 3000 });
  }
}
