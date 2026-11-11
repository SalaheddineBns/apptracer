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
 * Teams (SFD-05, ADMIN). RG-05-13: the back end returns the teams sorted by name and the members sorted by last name then
 * first name; the order received is kept and never re-sorted here.
 */
@Injectable({ providedIn: 'root' })
export class TeamsStore {
  private readonly api = inject(TeamsService);
  private requestId = 0;

  /** Deletions waiting for a response: one request at a time per team. */
  readonly pending = new PendingIds();

  readonly items = signal<TeamResponse[]>([]);
  readonly loading = signal(false);
  /** True as soon as a first response has arrived (tells the initial load apart from reloads). */
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);

  /** Reloads the list (the most recent response wins). */
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

  /** RG-05-02: active accounts with the MANAGER role offered as manager (sorted by the back end). */
  readonly managerCandidates = signal<TeamAccountSummary[]>([]);
  /** RG-05-03, RG-05-04, RG-05-11: USER or MANAGER accounts offered as members, with their current team. */
  readonly memberCandidates = signal<TeamMemberCandidateResponse[]>([]);
  readonly candidatesLoading = signal(false);
  readonly candidatesError = signal<string | null>(null);
  private candidatesRequestId = 0;

  /** Loads both candidate lists of the E2 dialog (re-read on each opening: accounts change). */
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

  /** RG-05-01 to RG-05-08: creation. The list is reloaded to follow the back end's order (RG-05-13). */
  create(request: CreateTeamRequest): Observable<TeamResponse> {
    return this.api.createTeam(request).pipe(tap(() => this.load()));
  }

  /** RG-05-01 to RG-05-08: update in one go (name, manager, full list of members) with the version received. */
  update(id: number, request: UpdateTeamRequest): Observable<TeamResponse> {
    return this.api.updateTeam(id, request).pipe(tap(() => this.load()));
  }

  /**
   * RG-05-09: permanent deletion. The members end up without a team on the back end; the row leaves the list without a reload.
   * Errors (e.g. 404, team already deleted) are shown by the error interceptor.
   */
  remove(id: number): Observable<unknown> {
    return this.pending.guard(id, () => this.api.deleteTeam(id).pipe(tap(() => this.items.update((list) => list.filter((team) => team.id !== id)))));
  }
}
