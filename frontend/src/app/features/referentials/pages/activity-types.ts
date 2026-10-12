import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ActivityTypeResponse } from '../../../core/api/generated';
import { ActivityTypeTag } from '../../../shared/components/activity-type-tag';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { ActivityTypesStore } from '../data/activity-types.store';

/** SFD-02 E1 Types d'activité (ADMIN) et E5 confirmation de désactivation. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TableModule, TagModule, ButtonModule, ActivityTypeTag, EmptyState, ErrorState, LoadingState],
  selector: 'app-activity-types',
  templateUrl: './activity-types.html',
})
export class ActivityTypesPage {
  protected readonly store = inject(ActivityTypesStore);
  private readonly confirmation = inject(ConfirmationService);
  private readonly messages = inject(MessageService);

  constructor() {
    // Une seule requête par ouverture de l'écran : la liste de l'ADMIN (avec les inactifs) est toujours relue.
    this.store.load();
  }

  /** E5, RG-02-05, RG-02-06 : confirmation avant de désactiver. */
  protected confirmDeactivate(type: ActivityTypeResponse): void {
    this.confirmation.confirm({
      header: 'Désactiver le type',
      message: `Désactiver le type « ${type.label} » ? Il ne sera plus proposé pour une nouvelle saisie. Les saisies existantes sont conservées.`,
      acceptLabel: 'Désactiver',
      rejectLabel: 'Annuler',
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => this.store.deactivate(type.id).subscribe(() => this.notify(`Le type « ${type.label} » est désactivé.`)),
    });
  }

  /** RG-02-05 : réversible, sans confirmation. */
  protected reactivate(type: ActivityTypeResponse): void {
    this.store.reactivate(type.id).subscribe(() => this.notify(`Le type « ${type.label} » est réactivé.`));
  }

  private notify(detail: string): void {
    this.messages.add({ severity: 'success', summary: 'Type mis à jour', detail, life: 3000 });
  }
}
