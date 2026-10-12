import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ProjectResponse } from '../../../core/api/generated';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { ProjectsStore } from '../data/projects.store';

/** SFD-02 E3 Projets (ADMIN) et E5 confirmation de désactivation. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TableModule, TagModule, ButtonModule, EmptyState, ErrorState, LoadingState],
  selector: 'app-projects',
  templateUrl: './projects.html',
})
export class ProjectsPage {
  protected readonly store = inject(ProjectsStore);
  private readonly confirmation = inject(ConfirmationService);
  private readonly messages = inject(MessageService);

  constructor() {
    // Une seule requête par ouverture de l'écran : la liste de l'ADMIN (avec les inactifs) est toujours relue.
    this.store.load();
  }

  /** E5, RG-02-05, RG-02-06 : confirmation avant de désactiver. */
  protected confirmDeactivate(project: ProjectResponse): void {
    this.confirmation.confirm({
      header: 'Désactiver le projet',
      message: `Désactiver le projet « ${project.name} » ? Il ne sera plus proposé pour une nouvelle saisie ni pour la modification d'une saisie. Les saisies existantes sont conservées.`,
      acceptLabel: 'Désactiver',
      rejectLabel: 'Annuler',
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => this.store.deactivate(project.id).subscribe(() => this.notify(`Le projet « ${project.name} » est désactivé.`)),
    });
  }

  /** RG-02-05 : réversible, sans confirmation. */
  protected reactivate(project: ProjectResponse): void {
    this.store.reactivate(project.id).subscribe(() => this.notify(`Le projet « ${project.name} » est réactivé.`));
  }

  private notify(detail: string): void {
    this.messages.add({ severity: 'success', summary: 'Projet mis à jour', detail, life: 3000 });
  }
}
