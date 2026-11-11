import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { JobTitleResponse } from '../../../core/api/generated';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { FocusRestorer } from '../../../shared/focus-restorer';
import { JobTitleDialog, JobTitleSaved } from '../components/job-title-dialog';
import { JobTitlesStore } from '../data/job-titles.store';

/** SFD-09 E1 Job titles (ADMIN) and E3 deactivation confirmation. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TableModule, TagModule, ButtonModule, EmptyState, ErrorState, LoadingState, JobTitleDialog, TPipe],
  selector: 'app-job-titles',
  templateUrl: './job-titles.html',
})
export class JobTitlesPage {
  protected readonly store = inject(JobTitlesStore);
  private readonly confirmation = inject(ConfirmationService);
  private readonly messages = inject(MessageService);
  private readonly focus = inject(FocusRestorer);
  private readonly i18n = inject(I18n);

  protected readonly dialogVisible = signal(false);
  /** Job title being edited; `null` for a creation. */
  protected readonly editing = signal<JobTitleResponse | null>(null);

  constructor() {
    // RG-09-07: the ADMIN's list (inactive ones included) is re-read each time the screen opens.
    this.store.load();
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.dialogVisible.set(true);
  }

  protected openEdit(jobTitle: JobTitleResponse): void {
    this.editing.set(jobTitle);
    this.dialogVisible.set(true);
  }

  protected onSaved(event: JobTitleSaved): void {
    const label = event.jobTitle.label;
    this.notify(
      this.i18n.t(event.mode === 'create' ? 'jobTitles.toast.created' : 'jobTitles.toast.updated'),
      this.i18n.t(event.mode === 'create' ? 'jobTitles.toast.createdDetail' : 'jobTitles.toast.updatedDetail', { label }),
    );
  }

  /** E3, RG-09-06: confirmation before deactivating. */
  protected confirmDeactivate(jobTitle: JobTitleResponse): void {
    this.confirmation.confirm({
      header: this.i18n.t('jobTitles.deactivate.header'),
      message: this.i18n.t('jobTitles.deactivate.message', { label: jobTitle.label }),
      acceptLabel: this.i18n.t('common.deactivate'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () =>
        this.store
          .deactivate(jobTitle.id)
          .subscribe(() => this.notify(this.i18n.t('jobTitles.toast.updated'), this.i18n.t('jobTitles.toast.deactivatedDetail', { label: jobTitle.label }))),
    });
  }

  /** RG-09-06: reversible, without confirmation. */
  protected reactivate(jobTitle: JobTitleResponse): void {
    // The button is disabled during the call, then replaced by Deactivate: the focus follows it.
    const anchor = this.focus.capture();
    this.store
      .reactivate(jobTitle.id)
      .pipe(finalize(() => this.focus.restore(anchor)))
      .subscribe(() => this.notify(this.i18n.t('jobTitles.toast.updated'), this.i18n.t('jobTitles.toast.reactivatedDetail', { label: jobTitle.label })));
  }

  private notify(summary: string, detail: string): void {
    this.messages.add({ severity: 'success', summary, detail, life: 3000 });
  }
}
