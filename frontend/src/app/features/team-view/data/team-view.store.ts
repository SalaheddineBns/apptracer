import { HttpContext } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { ManagedTeamResponse, TeamViewService } from '../../../core/api/generated';
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
}
