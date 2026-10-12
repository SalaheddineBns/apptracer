import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ProjectResponse } from '../../../core/api/generated';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { FocusRestorer } from '../../../shared/focus-restorer';
import { ProjectDialog, ProjectSaved } from '../components/project-dialog';
import { ProjectsStore } from '../data/projects.store';

/** SFD-02 E3 Projets (ADMIN) et E5 confirmation de désactivation. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TableModule, TagModule, ButtonModule, EmptyState, ErrorState, LoadingState, ProjectDialog],
  selector: 'app-projects',
  templateUrl: './projects.html',
})
export class ProjectsPage {
  protected readonly store = inject(ProjectsStore);
  private readonly confirmation = inject(ConfirmationService);
  private readonly messages = inject(MessageService);
  private readonly focus = inject(FocusRestorer);

  protected readonly dialogVisible = signal(false);
  /** Projet en cours de modification ; `null` pour une création. */
  protected readonly editing = signal<ProjectResponse | null>(null);

  constructor() {
    // Une seule requête par ouverture de l'écran : la liste de l'ADMIN (avec les inactifs) est toujours relue.
    this.store.load();
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.dialogVisible.set(true);
  }

  protected openEdit(project: ProjectResponse): void {
    this.editing.set(project);
    this.dialogVisible.set(true);
  }

  protected onSaved(event: ProjectSaved): void {
    this.notify(
      event.mode === 'create' ? 'Projet créé' : 'Projet mis à jour',
      event.mode === 'create' ? `Le projet « ${event.project.name} » est créé.` : `Le projet « ${event.project.name} » est modifié.`,
    );
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
      accept: () => this.store.deactivate(project.id).subscribe(() => this.notify('Projet mis à jour', `Le projet « ${project.name} » est désactivé.`)),
    });
  }

  /** RG-02-05 : réversible, sans confirmation. */
  protected reactivate(project: ProjectResponse): void {
    // Le bouton est désactivé pendant l'appel puis remplacé par « Désactiver » : le focus le suit.
    const anchor = this.focus.capture();
    this.store.reactivate(project.id)
      .pipe(finalize(() => this.focus.restore(anchor)))
      .subscribe(() => this.notify('Projet mis à jour', `Le projet « ${project.name} » est réactivé.`));
  }

  private notify(summary: string, detail: string): void {
    this.messages.add({ severity: 'success', summary, detail, life: 3000 });
  }
}
