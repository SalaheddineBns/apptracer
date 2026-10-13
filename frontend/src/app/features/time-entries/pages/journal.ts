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
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { isIsoDate, mondayOf, todayInParis } from '../../../shared/dates/week';
import { datesLabel } from '../../../shared/journal/time-entry.ui';
import { WeekJournal } from '../../../shared/journal/week-journal';
import { WeekNavigator } from '../../../shared/journal/week-navigator';
import { formatDuration } from '../../../shared/pipes/duration';
import { TimeEntryDialog, TimeEntrySaved } from '../components/time-entry-dialog';
import { TimeEntriesStore } from '../data/time-entries.store';

/** SFD-01 E1 Mon journal (USER et MANAGER, RG-01-14) : la semaine affichée est dans l'URL (`?semaine=YYYY-MM-DD`). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ButtonModule,
    SkeletonModule,
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

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  /** Lundi de la semaine affichée : celle de l'URL, sinon la semaine courante à Paris (RG-00-01). */
  protected readonly weekStart = computed(() => {
    const requested = this.params().get('semaine');
    return mondayOf(isIsoDate(requested) ? requested : todayInParis());
  });
  protected readonly skeletonDays = [0, 1, 2, 3, 4, 5, 6];
  protected readonly dialogVisible = signal(false);
  /** Saisie en cours de modification ; `null` pour une création. */
  protected readonly editing = signal<TimeEntryResponse | null>(null);

  constructor() {
    effect(() => {
      const weekStart = this.weekStart();
      untracked(() => this.store.load(weekStart));
    });
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.dialogVisible.set(true);
  }

  /** RG-01-10, RG-01-12 : le journal ne montre que ses propres saisies, toutes modifiables. */
  protected openEdit(entry: TimeEntryResponse): void {
    this.editing.set(entry);
    this.dialogVisible.set(true);
  }

  /** E3, RG-01-10, RG-01-11 : confirmation explicite avant de supprimer ; la saisie sort du journal et du total. */
  protected confirmRemove(entry: TimeEntryResponse): void {
    const name = `${entry.activityType.label} ${datesLabel(entry)}`;
    this.confirmation.confirm({
      header: 'Supprimer la saisie',
      message: `Supprimer la saisie « ${entry.activityType.label} » ${datesLabel(entry)} (${formatDuration(entry.durationMinutes)}) ?`,
      acceptLabel: 'Supprimer',
      rejectLabel: 'Annuler',
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () =>
        this.store.remove(entry.id).subscribe(() =>
          this.messages.add({
            severity: 'success',
            summary: 'Saisie supprimée',
            detail: `${name} est supprimée.`,
            life: 3000,
          }),
        ),
    });
  }

  /** CA-01-01 : la saisie enregistrée apparaît dans le journal, qui se place sur sa semaine. */
  protected onSaved(event: TimeEntrySaved): void {
    const { entry } = event;
    this.messages.add({
      severity: 'success',
      summary: event.mode === 'create' ? 'Saisie enregistrée' : 'Saisie modifiée',
      detail: `${entry.activityType.label} ${datesLabel(entry)} (${formatDuration(entry.durationMinutes)}).`,
      life: 3000,
    });
    const weekStart = mondayOf(entry.startDate);
    if (weekStart !== this.weekStart()) {
      this.goToWeek(weekStart);
    }
  }

  protected goToWeek(weekStart: string): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { semaine: weekStart } });
  }
}
