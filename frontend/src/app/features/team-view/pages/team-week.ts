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
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { formatDayMonth, formatWeekdayName, isIsoDate, mondayOf, todayInParis, weekDays } from '../../../shared/dates/week';
import { WeekNavigator } from '../../../shared/journal/week-navigator';
import { DecimalHoursPipe } from '../../../shared/pipes/duration';
import { TeamViewStore } from '../data/team-view.store';

/**
 * SFD-04 E1 Mon équipe (MANAGER) : l'équipe et la semaine affichées sont dans l'URL (`?equipe=<id>&semaine=YYYY-MM-DD`).
 * RG-04-05 : durées par jour et totaux viennent de l'API, jamais recalculés ici.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
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
  /** Lundi de la semaine affichée : celle de l'URL, sinon la semaine courante à Paris (RG-00-01). */
  protected readonly weekStart = computed(() => {
    const requested = this.params().get('semaine');
    return mondayOf(isIsoDate(requested) ? requested : todayInParis());
  });
  /** Équipe affichée : celle de l'URL, sinon la première par ordre alphabétique, déjà en tête de la réponse (RG-04-08). */
  protected readonly teamId = computed<number | null>(() => {
    const requested = Number(this.params().get('equipe'));
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
      queryParams: { semaine: weekStart },
      queryParamsHandling: 'merge',
    });
  }

  /** CA-04-10 : changer d'équipe garde la même semaine. */
  protected selectTeam(teamId: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { equipe: teamId, semaine: this.weekStart() },
    });
  }

  /** RG-04-02 : après un refus, retour à la première équipe du MANAGER, même semaine. */
  protected async showMyTeams(): Promise<void> {
    await this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { equipe: null, semaine: this.weekStart() },
      queryParamsHandling: 'merge',
    });
    // Le bouton cliqué disparaît avec le message de refus : le focus passe au titre de la page.
    this.title().nativeElement.focus();
  }

  protected memberName(member: { firstName: string; lastName: string }): string {
    return `${member.firstName} ${member.lastName}`.trim();
  }
}
