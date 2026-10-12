import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { Role, UserResponse } from '../../../core/api/generated';
import { AuthStore } from '../../../core/auth/auth.store';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { AccountsStore, isAccountSortField } from '../data/accounts.store';

const ROLE_LABELS: Record<Role, string> = {
  [Role.User]: 'Utilisateur',
  [Role.Manager]: 'Manager',
  [Role.Admin]: 'Administrateur',
};
const ROLE_SEVERITIES: Record<Role, 'secondary' | 'info' | 'contrast'> = {
  [Role.User]: 'secondary',
  [Role.Manager]: 'info',
  [Role.Admin]: 'contrast',
};

/** SFD-03 E4 Comptes (ADMIN). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TableModule, TagModule, ButtonModule, EmptyState, ErrorState, LoadingState],
  selector: 'app-accounts',
  templateUrl: './accounts.html',
})
export class AccountsPage {
  protected readonly store = inject(AccountsStore);
  private readonly auth = inject(AuthStore);
  private readonly confirmation = inject(ConfirmationService);
  private readonly messages = inject(MessageService);

  protected readonly first = computed(() => this.store.page() * this.store.size());
  protected readonly rowsPerPage = [10, 20, 50];

  constructor() {
    this.store.load();
  }

  protected roleLabel(role: Role): string {
    return ROLE_LABELS[role];
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

  /** Désactivation : confirmation préalable (RG-03-14 : ses sessions sont révoquées). */
  protected confirmDeactivate(user: UserResponse): void {
    this.confirmation.confirm({
      header: 'Désactiver le compte',
      message: `Désactiver le compte de ${this.fullName(user)} (${user.email}) ? Cette personne ne pourra plus se connecter et ses sessions seront fermées.`,
      acceptLabel: 'Désactiver',
      rejectLabel: 'Annuler',
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () =>
        this.store.deactivate(user.id).subscribe(() => this.notify(`Le compte de ${this.fullName(user)} est désactivé.`)),
    });
  }

  /** Réactivation : réversible, sans confirmation. */
  protected reactivate(user: UserResponse): void {
    this.store.reactivate(user.id).subscribe(() => this.notify(`Le compte de ${this.fullName(user)} est réactivé.`));
  }

  private notify(detail: string): void {
    this.messages.add({ severity: 'success', summary: 'Compte mis à jour', detail, life: 3000 });
  }
}
