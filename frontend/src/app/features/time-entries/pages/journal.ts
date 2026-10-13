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
import { ButtonModule } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { isIsoDate, mondayOf, todayInParis } from '../../../shared/dates/week';
import { WeekJournal } from '../../../shared/journal/week-journal';
import { WeekNavigator } from '../../../shared/journal/week-navigator';
import { TimeEntryDialog } from '../components/time-entry-dialog';
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

  constructor() {
    effect(() => {
      const weekStart = this.weekStart();
      untracked(() => this.store.load(weekStart));
    });
  }

  protected openCreate(): void {
    this.dialogVisible.set(true);
  }

  protected goToWeek(weekStart: string): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { semaine: weekStart } });
  }
}
