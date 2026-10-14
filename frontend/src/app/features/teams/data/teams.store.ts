import { HttpContext } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { forkJoin, Observable, tap } from 'rxjs';
import {
  CreateTeamRequest,
  UpdateTeamRequest,
  TeamAccountSummary,
  TeamMemberCandidateResponse,
  TeamResponse,
  TeamsService,
} from '../../../core/api/generated';
import { SKIP_ERROR_TOAST } from '../../../core/errors/error.interceptor';
import { toProblem } from '../../../core/errors/problem';
import { PendingIds } from '../../../shared/pending-ids';

/**
 * Équipes (SFD-05, ADMIN). RG-05-13 : le back renvoie les équipes triées par nom et les membres triés par nom puis prénom,
 * l'ordre reçu fait foi et n'est jamais retrié ici.
 */
@Injectable({ providedIn: 'root' })
export class TeamsStore {
  private readonly api = inject(TeamsService);
  private requestId = 0;

  /** Suppressions en attente de réponse : une seule requête à la fois par équipe. */
  readonly pending = new PendingIds();

  readonly items = signal<TeamResponse[]>([]);
  readonly loading = signal(false);
  /** Vrai dès qu'une première réponse est arrivée (distingue le chargement initial des rechargements). */
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);

  /** Recharge la liste (la réponse la plus récente l'emporte). */
  load(): void {
    const id = ++this.requestId;
    this.loading.set(true);
    this.error.set(null);
    // L'erreur est affichée par l'écran (état d'erreur « Réessayer »), pas dans un toast.
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    this.api.listTeams('body', false, { context }).subscribe({
      next: (list) => {
        if (id !== this.requestId) {
          return;
        }
        this.items.set(list);
        this.loaded.set(true);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        if (id !== this.requestId) {
          return;
        }
        this.error.set(toProblem(error).detail);
        this.loading.set(false);
      },
    });
  }

  /** RG-05-02 : comptes actifs de rôle MANAGER proposés comme manager (triés par le back). */
  readonly managerCandidates = signal<TeamAccountSummary[]>([]);
  /** RG-05-03, RG-05-04, RG-05-11 : comptes USER ou MANAGER proposés comme membres, avec leur équipe actuelle. */
  readonly memberCandidates = signal<TeamMemberCandidateResponse[]>([]);
  readonly candidatesLoading = signal(false);
  readonly candidatesError = signal<string | null>(null);
  private candidatesRequestId = 0;

  /** Charge les deux listes de candidats du dialogue E2 (relues à chaque ouverture : les comptes évoluent). */
  loadCandidates(): void {
    const id = ++this.candidatesRequestId;
    this.candidatesLoading.set(true);
    this.candidatesError.set(null);
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    forkJoin({
      managers: this.api.listTeamManagerCandidates('body', false, { context }),
      members: this.api.listTeamMemberCandidates('body', false, { context }),
    }).subscribe({
      next: ({ managers, members }) => {
        if (id !== this.candidatesRequestId) {
          return;
        }
        this.managerCandidates.set(managers);
        this.memberCandidates.set(members);
        this.candidatesLoading.set(false);
      },
      error: (error: unknown) => {
        if (id !== this.candidatesRequestId) {
          return;
        }
        this.candidatesError.set(toProblem(error).detail);
        this.candidatesLoading.set(false);
      },
    });
  }

  /** RG-05-01 à RG-05-08 : création. La liste est rechargée pour respecter l'ordre du back (RG-05-13). */
  create(request: CreateTeamRequest): Observable<TeamResponse> {
    return this.api.createTeam(request).pipe(tap(() => this.load()));
  }

  /** RG-05-01 à RG-05-08 : modification d'un bloc (nom, manager, liste complète des membres) avec la version reçue. */
  update(id: number, request: UpdateTeamRequest): Observable<TeamResponse> {
    return this.api.updateTeam(id, request).pipe(tap(() => this.load()));
  }

  /**
   * RG-05-09 : suppression définitive. Les membres se retrouvent sans équipe côté back ; la ligne quitte la liste sans la recharger.
   * Les erreurs (ex. 404, équipe déjà supprimée) sont affichées par l'intercepteur d'erreurs.
   */
  remove(id: number): Observable<unknown> {
    return this.pending.guard(id, () => this.api.deleteTeam(id).pipe(tap(() => this.items.update((list) => list.filter((team) => team.id !== id)))));
  }
}
