import { HttpContext } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import {
  CreateTimeEntryRequest,
  TimeEntriesService,
  TimeEntryResponse,
  UpdateTimeEntryRequest,
  WeeklyTimeEntriesResponse,
} from '../../../core/api/generated';
import { SKIP_ERROR_TOAST } from '../../../core/errors/error.interceptor';
import { toProblem } from '../../../core/errors/problem';
import { PendingIds } from '../../../shared/pending-ids';

/**
 * Mes saisies d'une semaine (SFD-01 E1). RG-00-03 : le back ne renvoie que les saisies du compte connecté.
 * RG-01-13 : le total de la semaine vient de l'API (`totalMinutes`), il n'est jamais recalculé ici.
 */
@Injectable({ providedIn: 'root' })
export class TimeEntriesStore {
  private readonly api = inject(TimeEntriesService);
  private requestId = 0;

  /** Suppressions en attente de réponse : une seule requête à la fois par saisie. */
  readonly pending = new PendingIds();

  /** Lundi de la semaine demandée (`YYYY-MM-DD`), null tant qu'aucune semaine n'a été demandée. */
  readonly weekStart = signal<string | null>(null);
  /** Semaine affichée ; null pendant le premier chargement d'une semaine ou après une erreur. */
  readonly week = signal<WeeklyTimeEntriesResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Charge la semaine qui commence le lundi donné (la réponse la plus récente l'emporte). */
  load(weekStart: string): void {
    if (weekStart !== this.weekStart()) {
      // Changement de semaine : les saisies de l'ancienne ne restent pas affichées sous le nouveau libellé.
      this.week.set(null);
    }
    this.weekStart.set(weekStart);
    const id = ++this.requestId;
    this.loading.set(true);
    this.error.set(null);
    // L'erreur est affichée par l'écran (état d'erreur « Réessayer »), pas dans un toast.
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    this.api.listMyTimeEntries(weekStart, 'body', false, { context }).subscribe({
      next: (week) => {
        if (id !== this.requestId) {
          return;
        }
        this.week.set(week);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        if (id !== this.requestId) {
          return;
        }
        this.week.set(null);
        this.error.set(toProblem(error).detail);
        this.loading.set(false);
      },
    });
  }

  /** Recharge la semaine affichée (saisies et total). */
  reload(): void {
    const weekStart = this.weekStart();
    if (weekStart !== null) {
      this.load(weekStart);
    }
  }

  /** RG-01-01 à RG-01-09 : création. La semaine est rechargée : le total vient du back (RG-01-13). */
  create(request: CreateTimeEntryRequest): Observable<TimeEntryResponse> {
    return this.api.createTimeEntry(request).pipe(tap(() => this.reload()));
  }

  /** RG-01-12 : modification, avec la version lue. */
  update(id: number, request: UpdateTimeEntryRequest): Observable<TimeEntryResponse> {
    return this.api.updateTimeEntry(id, request).pipe(tap(() => this.reload()));
  }

  /** RG-01-10, RG-01-11 : suppression (logique côté back) ; la saisie sort du journal et du total. */
  remove(id: number): Observable<unknown> {
    return this.pending.guard(id, () =>
      this.api.deleteTimeEntry(id).pipe(tap(() => this.reload())),
    );
  }
}
