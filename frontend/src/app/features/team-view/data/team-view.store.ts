import { HttpContext } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import {
  ManagedTeamResponse,
  MemberWeeklyTimeEntriesResponse,
  TeamViewService,
  TeamWeekSummaryResponse,
} from '../../../core/api/generated';
import { SKIP_ERROR_TOAST } from '../../../core/errors/error.interceptor';
import { toProblem } from '../../../core/errors/problem';

/**
 * Team view (SFD-04, MANAGER). RG-04-08: the back end returns the teams sorted by name; the order received is kept and the first
 * is the default team; it is never re-sorted here. RG-04-05: durations and totals come from the API, never recomputed.
 */
@Injectable({ providedIn: 'root' })
export class TeamViewStore {
  private readonly api = inject(TeamViewService);
  private teamsRequestId = 0;

  /** Teams managed by the signed-in MANAGER (RG-04-01, RG-04-06). */
  readonly teams = signal<ManagedTeamResponse[]>([]);
  readonly teamsLoading = signal(false);
  /** True as soon as a first response has arrived (tells the initial load apart from reloads). */
  readonly teamsLoaded = signal(false);
  readonly teamsError = signal<string | null>(null);

  /** Reloads the managed teams (the most recent response wins). */
  loadTeams(): void {
    const id = ++this.teamsRequestId;
    this.teamsLoading.set(true);
    this.teamsError.set(null);
    // L'erreur est affichée par l'écran (état d'erreur « Réessayer »), pas dans un toast.
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    this.api.listMyManagedTeams('body', false, { context }).subscribe({
      next: (teams) => {
        if (id !== this.teamsRequestId) {
          return;
        }
        this.teams.set(teams);
        this.teamsLoaded.set(true);
        this.teamsLoading.set(false);
      },
      error: (error: unknown) => {
        if (id !== this.teamsRequestId) {
          return;
        }
        this.teamsError.set(toProblem(error).detail);
        this.teamsLoading.set(false);
      },
    });
  }

  /** A team's week (members × seven days); null during the first load or after an error. */
  readonly summary = signal<TeamWeekSummaryResponse | null>(null);
  readonly summaryError = signal<string | null>(null);
  /** RG-04-02: 403, team outside the MANAGER's scope (stale link or removed team); no Retry possible. */
  readonly summaryForbidden = signal(false);
  private summaryKey: { teamId: number; weekStart: string } | null = null;
  private summaryRequestId = 0;

  /** Loads the week `weekStart` (a Monday) of the team (the most recent response wins). */
  loadSummary(teamId: number, weekStart: string): void {
    this.summaryKey = { teamId, weekStart };
    // Changement d'équipe ou de semaine : l'ancien tableau ne reste pas affiché sous le nouveau libellé.
    this.summary.set(null);
    this.summaryError.set(null);
    this.summaryForbidden.set(false);
    const id = ++this.summaryRequestId;
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    this.api.getTeamWeekSummary(teamId, weekStart, 'body', false, { context }).subscribe({
      next: (summary) => {
        if (id === this.summaryRequestId) {
          this.summary.set(summary);
        }
      },
      error: (error: unknown) => {
        if (id !== this.summaryRequestId) {
          return;
        }
        const problem = toProblem(error);
        this.summaryForbidden.set(problem.status === 403);
        this.summaryError.set(problem.detail);
      },
    });
  }

  /** Reloads the displayed team and week. */
  reloadSummary(): void {
    if (this.summaryKey) {
      this.loadSummary(this.summaryKey.teamId, this.summaryKey.weekStart);
    }
  }

  /** A member's journal (E2); null during the first load or after an error. */
  readonly memberJournal = signal<MemberWeeklyTimeEntriesResponse | null>(null);
  readonly memberError = signal<string | null>(null);
  /** RG-04-02: 403, account outside the MANAGER's scope (removed member, stale link); no Retry possible. */
  readonly memberForbidden = signal(false);
  private memberKey: { userId: number; weekStart: string } | null = null;
  private memberRequestId = 0;

  /** Loads the week `weekStart` (a Monday) of the member (the most recent response wins). */
  loadMemberJournal(userId: number, weekStart: string): void {
    this.memberKey = { userId, weekStart };
    // Changement de membre ou de semaine : l'ancien journal ne reste pas affiché sous le nouveau libellé.
    this.memberJournal.set(null);
    this.memberError.set(null);
    this.memberForbidden.set(false);
    const id = ++this.memberRequestId;
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    this.api.listTeamMemberTimeEntries(userId, weekStart, 'body', false, { context }).subscribe({
      next: (journal) => {
        if (id === this.memberRequestId) {
          this.memberJournal.set(journal);
        }
      },
      error: (error: unknown) => {
        if (id !== this.memberRequestId) {
          return;
        }
        const problem = toProblem(error);
        this.memberForbidden.set(problem.status === 403);
        this.memberError.set(problem.detail);
      },
    });
  }

  /** Reloads the displayed member and week. */
  reloadMemberJournal(): void {
    if (this.memberKey) {
      this.loadMemberJournal(this.memberKey.userId, this.memberKey.weekStart);
    }
  }
}
