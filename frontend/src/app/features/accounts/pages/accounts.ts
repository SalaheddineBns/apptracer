import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { Role, UserResponse } from '../../../core/api/generated';
import { AuthStore } from '../../../core/auth/auth.store';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { FocusRestorer } from '../../../shared/focus-restorer';
import { AccountDialog, AccountSaved } from '../components/account-dialog';
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
  imports: [TableModule, TagModule, ButtonModule, EmptyState, ErrorState, LoadingState, AccountDialog],
  selector: 'app-accounts',
  templateUrl: './accounts.html',
})
export class AccountsPage {
  protected readonly store = inject(AccountsStore);
  private readonly auth = inject(AuthStore);
  private readonly confirmation = inject(ConfirmationService);
  private readonly messages = inject(MessageService);
  private readonly focus = inject(FocusRestorer);

  protected readonly first = computed(() => this.store.page() * this.store.size());
  protected readonly rowsPerPage = [10, 20, 50];

  protected readonly dialogVisible = signal(false);
  /** Compte en cours de modification ; `null` pour une création. */
  protected readonly editing = signal<UserResponse | null>(null);

  constructor() {
    this.store.load();
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
    this.notify(
      event.mode === 'create' ? 'Compte créé' : 'Compte mis à jour',
      event.mode === 'create' ? `Le compte de ${this.fullName(event.user)} est créé.` : `Le compte de ${this.fullName(event.user)} est modifié.`,
    );
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
        this.store.deactivate(user.id).subscribe({
          next: () => this.notify('Compte mis à jour', `Le compte de ${this.fullName(user)} est désactivé.`),
          // RG-05-10 (manager d'une équipe, 409 nommant l'équipe), RG-03-13 : le refus est affiché en toast par l'intercepteur
          // d'erreurs, la ligne reste inchangée (« Actif ») et son bouton est de nouveau disponible.
          error: () => undefined,
        }),
    });
  }

  /** Réactivation : réversible, sans confirmation. */
  protected reactivate(user: UserResponse): void {
    // Le bouton est désactivé pendant l'appel puis remplacé par « Désactiver » : le focus le suit.
    const anchor = this.focus.capture();
    this.store.reactivate(user.id)
      .pipe(finalize(() => this.focus.restore(anchor)))
      .subscribe(() => this.notify('Compte mis à jour', `Le compte de ${this.fullName(user)} est réactivé.`));
  }

  private notify(summary: string, detail: string): void {
    this.messages.add({ severity: 'success', summary, detail, life: 3000 });
  }
}
