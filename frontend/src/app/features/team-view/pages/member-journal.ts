import { ChangeDetectionStrategy, Component, computed, effect, inject, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { isIsoDate, mondayOf, todayInParis } from '../../../shared/dates/week';
import { WeekJournal } from '../../../shared/journal/week-journal';
import { WeekNavigator } from '../../../shared/journal/week-navigator';
import { TeamViewStore } from '../data/team-view.store';

/**
 * SFD-04 E2 Journal d'un membre (MANAGER), en lecture seule (RG-04-03) : le membre est dans le chemin, la semaine dans l'URL
 * (`/team/members/:userId?week=YYYY-MM-DD`). RG-04-05 : le total vient de l'API, jamais recalculé ici.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonModule, MessageModule, SkeletonModule, TagModule, EmptyState, ErrorState, WeekJournal, WeekNavigator],
  selector: 'app-member-journal',
  templateUrl: './member-journal.html',
})
export class MemberJournalPage {
  protected readonly store = inject(TeamViewStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  private readonly path = toSignal(this.route.paramMap, { initialValue: this.route.snapshot.paramMap });
  /** Lundi de la semaine affichée : celle de l'URL, sinon la semaine courante à Paris (RG-00-01). */
  protected readonly weekStart = computed(() => {
    const requested = this.params().get('week');
    return mondayOf(isIsoDate(requested) ? requested : todayInParis());
  });
  /** Compte du chemin ; null s'il n'est pas un identifiant (aucun appel, même message que pour un compte hors périmètre). */
  protected readonly userId = computed<number | null>(() => {
    const id = Number(this.path().get('userId'));
    return Number.isInteger(id) && id > 0 ? id : null;
  });
  protected readonly skeletonDays = [0, 1, 2, 3, 4, 5, 6];
  protected readonly forbidden = computed(() => this.userId() === null || this.store.memberForbidden());
  protected readonly memberName = computed(() => {
    const member = this.store.memberJournal()?.member;
    return member ? `${member.firstName} ${member.lastName}`.trim() : null;
  });
  /** Retour vers E1 en conservant la semaine et, une fois connue, l'équipe du membre (RG-04-04). */
  protected readonly backParams = computed(() => {
    // L'équipe renvoyée par l'API prime ; sinon celle d'origine, portée par l'URL du lien de membre.
    const requested = Number(this.params().get('team'));
    const fromUrl = Number.isInteger(requested) && requested > 0 ? requested : null;
    const team = this.store.memberJournal()?.team?.id ?? fromUrl;
    return team !== null ? { team, week: this.weekStart() } : { week: this.weekStart() };
  });

  constructor() {
    effect(() => {
      const userId = this.userId();
      const weekStart = this.weekStart();
      if (userId !== null) {
        untracked(() => this.store.loadMemberJournal(userId, weekStart));
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

  protected backToTeam(): void {
    void this.router.navigate(['/team'], { queryParams: this.backParams() });
  }
}
