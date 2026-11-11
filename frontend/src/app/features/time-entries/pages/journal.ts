import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { TimeEntryResponse } from '../../../core/api/generated';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { isIsoDate, mondayOf, todayInParis } from '../../../shared/dates/week';
import { datesLabel } from '../../../shared/journal/time-entry.ui';
import { WeekJournal } from '../../../shared/journal/week-journal';
import { WeekNavigator } from '../../../shared/journal/week-navigator';
import { formatDuration } from '../../../shared/pipes/duration';
import { TimeEntryDialog, TimeEntrySaved } from '../components/time-entry-dialog';
import { TimeEntriesStore } from '../data/time-entries.store';

/** SFD-01 E1 My journal (USER and MANAGER, RG-01-14): the displayed week is in the URL (`?week=YYYY-MM-DD`). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ButtonModule,
    SkeletonModule,
    TPipe,
    EmptyState,
    ErrorState,
    WeekJournal,
    WeekNavigator,
    TimeEntryDialog,
  ],
  selector: 'app-journal',
  templateUrl: './journal.html',
})
export class JournalPage {
  protected readonly store = inject(TimeEntriesStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly i18n = inject(I18n);

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  /** Monday of the displayed week: the URL's, otherwise the current week in Paris (RG-00-01). */
  protected readonly weekStart = computed(() => {
    const requested = this.params().get('week');
    return mondayOf(isIsoDate(requested) ? requested : todayInParis());
  });
  protected readonly skeletonDays = [0, 1, 2, 3, 4, 5, 6];
  protected readonly dialogVisible = signal(false);
  /** Time entry being edited; `null` for a creation. */
  protected readonly editing = signal<TimeEntryResponse | null>(null);
  /** Start date offered on creation, when it comes from a day's +. */
  protected readonly initialDate = signal<string | null>(null);

  constructor() {
    effect(() => {
      const weekStart = this.weekStart();
      untracked(() => this.store.load(weekStart));
    });
  }

  /** `day`: date of a journal day's +; without it (New time entry button), today's date is offered. */
  protected openCreate(day: string | null = null): void {
    this.initialDate.set(day);
    this.editing.set(null);
    this.dialogVisible.set(true);
  }

  /** RG-01-10, RG-01-12: the journal shows only one's own time entries, all editable. */
  protected openEdit(entry: TimeEntryResponse): void {
    this.editing.set(entry);
    this.dialogVisible.set(true);
  }

  /** E3, RG-01-10, RG-01-11: explicit confirmation before deleting; the entry leaves the journal and the total. */
  protected confirmRemove(entry: TimeEntryResponse): void {
    const name = `${entry.activityType.label} ${datesLabel(entry, this.i18n)}`;
    this.confirmation.confirm({
      header: this.i18n.t('journal.delete.header'),
      message: this.i18n.t('journal.delete.message', {
        label: entry.activityType.label,
        dates: datesLabel(entry, this.i18n),
        duration: formatDuration(entry.durationMinutes),
      }),
      acceptLabel: this.i18n.t('common.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () =>
        this.store.remove(entry.id).subscribe(() =>
          this.messages.add({
            severity: 'success',
            summary: this.i18n.t('journal.delete.deleted'),
            detail: this.i18n.t('journal.delete.deletedDetail', { name }),
            life: 3000,
          }),
        ),
    });
  }

  /** CA-01-01: the saved time entry appears in the journal, which moves to its week. */
  protected onSaved(event: TimeEntrySaved): void {
    const { entry } = event;
    this.messages.add({
      severity: 'success',
      summary: this.i18n.t(event.mode === 'create' ? 'journal.saved' : 'journal.updated'),
      detail: this.i18n.t('journal.savedDetail', {
        name: `${entry.activityType.label} ${datesLabel(entry, this.i18n)}`,
        duration: formatDuration(entry.durationMinutes),
      }),
      life: 3000,
    });
    const weekStart = mondayOf(entry.startDate);
    if (weekStart !== this.weekStart()) {
      this.goToWeek(weekStart);
    }
  }

  protected goToWeek(weekStart: string): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { week: weekStart } });
  }
}
