import { HttpContext } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { ManagedTeamResponse, TeamViewService, TeamWeekSummaryResponse } from '../../../core/api/generated';
import { SKIP_ERROR_TOAST } from '../../../core/errors/error.interceptor';
import { toProblem } from '../../../core/errors/problem';

/**
 * Vue équipe (SFD-04, MANAGER). RG-04-08 : le back renvoie les équipes triées par nom, l'ordre reçu fait foi et la première
 * est l'équipe par défaut ; il n'est jamais retrié ici. RG-04-05 : les durées et totaux viennent de l'API, jamais recalculés.
 */
@Injectable({ providedIn: 'root' })
export class TeamViewStore {
  private readonly api = inject(TeamViewService);
  private teamsRequestId = 0;

  /** Équipes dont le MANAGER connecté est manager (RG-04-01, RG-04-06). */
  readonly teams = signal<ManagedTeamResponse[]>([]);
  readonly teamsLoading = signal(false);
  /** Vrai dès qu'une première réponse est arrivée (distingue le chargement initial des rechargements). */
  readonly teamsLoaded = signal(false);
  readonly teamsError = signal<string | null>(null);

  /** Recharge les équipes gérées (la réponse la plus récente l'emporte). */
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

  /** Semaine d'une équipe (membres × sept jours) ; null pendant le premier chargement ou après une erreur. */
  readonly summary = signal<TeamWeekSummaryResponse | null>(null);
  readonly summaryError = signal<string | null>(null);
  /** RG-04-02 : 403, équipe hors du périmètre du MANAGER (lien périmé ou équipe retirée) ; pas de « Réessayer » possible. */
  readonly summaryForbidden = signal(false);
  private summaryKey: { teamId: number; weekStart: string } | null = null;
  private summaryRequestId = 0;

  /** Charge la semaine `weekStart` (un lundi) de l'équipe (la réponse la plus récente l'emporte). */
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

  /** Recharge l'équipe et la semaine affichées. */
  reloadSummary(): void {
    if (this.summaryKey) {
      this.loadSummary(this.summaryKey.teamId, this.summaryKey.weekStart);
    }
  }
}
