import { ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, untracked, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { formatDayMonth, formatWeekdayName, isIsoDate, mondayOf, todayInParis, weekDays } from '../../../shared/dates/week';
import { WeekNavigator } from '../../../shared/journal/week-navigator';
import { DecimalHoursPipe } from '../../../shared/pipes/duration';
import { TeamViewStore } from '../data/team-view.store';

/**
 * SFD-04 E1 My team (MANAGER): the displayed team and week are in the URL (`?team=<id>&week=YYYY-MM-DD`).
 * RG-04-05: per-day durations and totals come from the API, never recomputed here.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TPipe,
    RouterLink,
    ButtonModule,
    MessageModule,
    SelectModule,
    SkeletonModule,
    TableModule,
    TagModule,
    EmptyState,
    ErrorState,
    LoadingState,
    WeekNavigator,
    DecimalHoursPipe,
  ],
  selector: 'app-team-week',
  templateUrl: './team-week.html',
})
export class TeamWeekPage {
  protected readonly store = inject(TeamViewStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18n);
  private readonly title = viewChild.required<ElementRef<HTMLElement>>('title');

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  /** Monday of the displayed week: the URL's, otherwise the current week in Paris (RG-00-01). */
  protected readonly weekStart = computed(() => {
    const requested = this.params().get('week');
    return mondayOf(isIsoDate(requested) ? requested : todayInParis());
  });
  /**
   * Displayed team: the URL's, otherwise the first in alphabetical order, already at the top of the response (RG-04-08).
   */
  protected readonly teamId = computed<number | null>(() => {
    const requested = Number(this.params().get('team'));
    if (Number.isInteger(requested) && requested > 0) {
      return requested;
    }
    return this.store.teams()[0]?.id ?? null;
  });
  protected readonly days = computed(() => {
    const lang = this.i18n.lang();
    return weekDays(this.weekStart()).map((iso) => ({ iso, name: formatWeekdayName(iso, lang), label: formatDayMonth(iso) }));
  });

  constructor() {
    // Une seule requête d'équipes par ouverture de l'écran.
    this.store.loadTeams();
    effect(() => {
      const teamId = this.teamId();
      const weekStart = this.weekStart();
      if (this.store.teamsLoaded() && teamId !== null) {
        untracked(() => this.store.loadSummary(teamId, weekStart));
      }
    });
  }

  protected goToWeek(weekStart: string): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { week: weekStart },
      queryParamsHandling: 'merge',
    });
  }

  /** CA-04-10: changing team keeps the same week. */
  protected selectTeam(teamId: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { team: teamId, week: this.weekStart() },
    });
  }

  /** RG-04-02: after a refusal, back to the MANAGER's first team, same week. */
  protected async showMyTeams(): Promise<void> {
    await this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { team: null, week: this.weekStart() },
      queryParamsHandling: 'merge',
    });
    // Le bouton cliqué disparaît avec le message de refus : le focus passe au titre de la page.
    this.title().nativeElement.focus();
  }

  protected memberName(member: { firstName: string; lastName: string }): string {
    return `${member.firstName} ${member.lastName}`.trim();
  }
}
