import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ProjectResponse } from '../../../core/api/generated';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { FocusRestorer } from '../../../shared/focus-restorer';
import { ProjectDialog, ProjectSaved } from '../components/project-dialog';
import { ProjectsStore } from '../data/projects.store';

/** SFD-02 E3 Projects (ADMIN) and E5 deactivation confirmation. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TableModule, TagModule, ButtonModule, EmptyState, ErrorState, LoadingState, ProjectDialog, TPipe],
  selector: 'app-projects',
  templateUrl: './projects.html',
})
export class ProjectsPage {
  protected readonly store = inject(ProjectsStore);
  private readonly confirmation = inject(ConfirmationService);
  private readonly messages = inject(MessageService);
  private readonly focus = inject(FocusRestorer);
  private readonly i18n = inject(I18n);

  protected readonly dialogVisible = signal(false);
  /** Project being edited; `null` for a creation. */
  protected readonly editing = signal<ProjectResponse | null>(null);

  constructor() {
    // One request per opening of the screen: the ADMIN's list (inactive ones included) is always re-read.
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
    const name = event.project.name;
    this.notify(
      this.i18n.t(event.mode === 'create' ? 'projects.toast.created' : 'projects.toast.updated'),
      this.i18n.t(event.mode === 'create' ? 'projects.toast.createdDetail' : 'projects.toast.updatedDetail', { name }),
    );
  }

  /** E5, RG-02-05, RG-02-06: confirmation before deactivating. */
  protected confirmDeactivate(project: ProjectResponse): void {
    this.confirmation.confirm({
      header: this.i18n.t('projects.deactivate.header'),
      message: this.i18n.t('projects.deactivate.message', { name: project.name }),
      acceptLabel: this.i18n.t('common.deactivate'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => this.store.deactivate(project.id).subscribe(() => this.notify(this.i18n.t('projects.toast.updated'), this.i18n.t('projects.toast.deactivatedDetail', { name: project.name }))),
    });
  }

  /** RG-02-05: reversible, without confirmation. */
  protected reactivate(project: ProjectResponse): void {
    // The button is disabled during the call, then replaced by Deactivate: the focus follows it.
    const anchor = this.focus.capture();
    this.store.reactivate(project.id)
      .pipe(finalize(() => this.focus.restore(anchor)))
      .subscribe(() => this.notify(this.i18n.t('projects.toast.updated'), this.i18n.t('projects.toast.reactivatedDetail', { name: project.name })));
  }

  private notify(summary: string, detail: string): void {
    this.messages.add({ severity: 'success', summary, detail, life: 3000 });
  }
}
