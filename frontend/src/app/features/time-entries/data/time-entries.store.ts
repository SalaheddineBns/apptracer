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
 * My time entries for one week (SFD-01 E1). RG-00-03: the back end returns only the signed-in account's entries.
 * RG-01-13: the week total comes from the API (`totalMinutes`); it is never recomputed here.
 */
@Injectable({ providedIn: 'root' })
export class TimeEntriesStore {
  private readonly api = inject(TimeEntriesService);
  private requestId = 0;

  /** Deletions waiting for a response: one request at a time per time entry. */
  readonly pending = new PendingIds();

  /** Monday of the requested week (`YYYY-MM-DD`), null until a week has been requested. */
  readonly weekStart = signal<string | null>(null);
  /** Displayed week; null during the first load of a week or after an error. */
  readonly week = signal<WeeklyTimeEntriesResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Loads the week starting on the given Monday (the most recent response wins). */
  load(weekStart: string): void {
    if (weekStart !== this.weekStart()) {
      // Week change: the old week's time entries do not stay displayed under the new label.
      this.week.set(null);
    }
    this.weekStart.set(weekStart);
    const id = ++this.requestId;
    this.loading.set(true);
    this.error.set(null);
    // The error is shown by the screen (error state with Retry), not in a toast.
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

  /** Reloads the displayed week (time entries and total). */
  reload(): void {
    const weekStart = this.weekStart();
    if (weekStart !== null) {
      this.load(weekStart);
    }
  }

  /** RG-01-01 to RG-01-09: creation. The week is reloaded: the total comes from the back end (RG-01-13). */
  create(request: CreateTimeEntryRequest): Observable<TimeEntryResponse> {
    return this.api.createTimeEntry(request).pipe(tap(() => this.reload()));
  }

  /** RG-01-12: update, with the version read. */
  update(id: number, request: UpdateTimeEntryRequest): Observable<TimeEntryResponse> {
    return this.api.updateTimeEntry(id, request).pipe(tap(() => this.reload()));
  }

  /** RG-01-10, RG-01-11: deletion (logical on the back end); the entry leaves the journal and the total. */
  remove(id: number): Observable<unknown> {
    return this.pending.guard(id, () =>
      this.api.deleteTimeEntry(id).pipe(tap(() => this.reload())),
    );
  }
}
