import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ActivityTypeResponse } from '../../../core/api/generated';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { ActivityTypeTag } from '../../../shared/components/activity-type-tag';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { FocusRestorer } from '../../../shared/focus-restorer';
import { ActivityTypeDialog, ActivityTypeSaved } from '../components/activity-type-dialog';
import { ActivityTypesStore } from '../data/activity-types.store';

/** SFD-02 E1 Activity types (ADMIN) and E5 deactivation confirmation. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TableModule, TagModule, ButtonModule, ActivityTypeTag, EmptyState, ErrorState, LoadingState, ActivityTypeDialog, TPipe],
  selector: 'app-activity-types',
  templateUrl: './activity-types.html',
})
export class ActivityTypesPage {
  protected readonly store = inject(ActivityTypesStore);
  private readonly confirmation = inject(ConfirmationService);
  private readonly messages = inject(MessageService);
  private readonly focus = inject(FocusRestorer);
  private readonly i18n = inject(I18n);

  protected readonly dialogVisible = signal(false);
  /** Type being edited; `null` for a creation. */
  protected readonly editing = signal<ActivityTypeResponse | null>(null);

  constructor() {
    // One request per opening of the screen: the ADMIN's list (inactive ones included) is always re-read.
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
    const label = event.type.label;
    this.notify(
      this.i18n.t(event.mode === 'create' ? 'activityTypes.toast.created' : 'activityTypes.toast.updated'),
      this.i18n.t(event.mode === 'create' ? 'activityTypes.toast.createdDetail' : 'activityTypes.toast.updatedDetail', { label }),
    );
  }

  /** E5, RG-02-05, RG-02-06: confirmation before deactivating. */
  protected confirmDeactivate(type: ActivityTypeResponse): void {
    this.confirmation.confirm({
      header: this.i18n.t('activityTypes.deactivate.header'),
      message: this.i18n.t('activityTypes.deactivate.message', { label: type.label }),
      acceptLabel: this.i18n.t('common.deactivate'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => this.store.deactivate(type.id).subscribe(() => this.notify(this.i18n.t('activityTypes.toast.updated'), this.i18n.t('activityTypes.toast.deactivatedDetail', { label: type.label }))),
    });
  }

  /** RG-02-05: reversible, without confirmation. */
  protected reactivate(type: ActivityTypeResponse): void {
    // The button is disabled during the call, then replaced by Deactivate: the focus follows it.
    const anchor = this.focus.capture();
    this.store.reactivate(type.id)
      .pipe(finalize(() => this.focus.restore(anchor)))
      .subscribe(() => this.notify(this.i18n.t('activityTypes.toast.updated'), this.i18n.t('activityTypes.toast.reactivatedDetail', { label: type.label })));
  }

  private notify(summary: string, detail: string): void {
    this.messages.add({ severity: 'success', summary, detail, life: 3000 });
  }
}
