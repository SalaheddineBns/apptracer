import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ActivityTypeResponse } from '../../../core/api/generated';
import { ActivityTypeTag } from '../../../shared/components/activity-type-tag';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { FocusRestorer } from '../../../shared/focus-restorer';
import { ActivityTypeDialog, ActivityTypeSaved } from '../components/activity-type-dialog';
import { ActivityTypesStore } from '../data/activity-types.store';

/** SFD-02 E1 Types d'activité (ADMIN) et E5 confirmation de désactivation. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TableModule, TagModule, ButtonModule, ActivityTypeTag, EmptyState, ErrorState, LoadingState, ActivityTypeDialog],
  selector: 'app-activity-types',
  templateUrl: './activity-types.html',
})
export class ActivityTypesPage {
  protected readonly store = inject(ActivityTypesStore);
  private readonly confirmation = inject(ConfirmationService);
  private readonly messages = inject(MessageService);
  private readonly focus = inject(FocusRestorer);

  protected readonly dialogVisible = signal(false);
  /** Type en cours de modification ; `null` pour une création. */
  protected readonly editing = signal<ActivityTypeResponse | null>(null);

  constructor() {
    // Une seule requête par ouverture de l'écran : la liste de l'ADMIN (avec les inactifs) est toujours relue.
    this.store.load();
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.dialogVisible.set(true);
  }

  protected openEdit(type: ActivityTypeResponse): void {
    this.editing.set(type);
    this.dialogVisible.set(true);
  }

  protected onSaved(event: ActivityTypeSaved): void {
    this.notify(
      event.mode === 'create' ? 'Type créé' : 'Type mis à jour',
      event.mode === 'create' ? `Le type « ${event.type.label} » est créé.` : `Le type « ${event.type.label} » est modifié.`,
    );
  }

  /** E5, RG-02-05, RG-02-06 : confirmation avant de désactiver. */
  protected confirmDeactivate(type: ActivityTypeResponse): void {
    this.confirmation.confirm({
      header: 'Désactiver le type',
      message: `Désactiver le type « ${type.label} » ? Il ne sera plus proposé pour une nouvelle saisie ni pour la modification d'une saisie. Les saisies existantes sont conservées.`,
      acceptLabel: 'Désactiver',
      rejectLabel: 'Annuler',
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => this.store.deactivate(type.id).subscribe(() => this.notify('Type mis à jour', `Le type « ${type.label} » est désactivé.`)),
    });
  }

  /** RG-02-05 : réversible, sans confirmation. */
  protected reactivate(type: ActivityTypeResponse): void {
    // Le bouton est désactivé pendant l'appel puis remplacé par « Désactiver » : le focus le suit.
    const anchor = this.focus.capture();
    this.store.reactivate(type.id)
      .pipe(finalize(() => this.focus.restore(anchor)))
      .subscribe(() => this.notify('Type mis à jour', `Le type « ${type.label} » est réactivé.`));
  }

  private notify(summary: string, detail: string): void {
    this.messages.add({ severity: 'success', summary, detail, life: 3000 });
  }
}
