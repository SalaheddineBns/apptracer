import { HttpContext } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { CreateJobTitleRequest, JobTitleResponse, JobTitlesService, UpdateJobTitleRequest } from '../../../core/api/generated';
import { SKIP_ERROR_TOAST } from '../../../core/errors/error.interceptor';
import { toProblem } from '../../../core/errors/problem';
import { PendingIds } from '../../../shared/pending-ids';

/**
 * Employee job titles (SFD-09). RG-09-07: the back end returns only the active ones for USER and MANAGER, all for ADMIN.
 * RG-09-08: the order returned (alphabetical by label) is kept; it is never re-sorted here.
 */
@Injectable({ providedIn: 'root' })
export class JobTitlesStore {
  private readonly api = inject(JobTitlesService);
  private requestId = 0;

  /** Deactivations / reactivations waiting for a response: one request at a time per row. */
  readonly pending = new PendingIds();

  readonly items = signal<JobTitleResponse[]>([]);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);
  /** RG-09-06: only active job titles are offered for assignment (same order as the list). */
  readonly activeItems = computed(() => this.items().filter((item) => item.active));

  /** Loads the list once: no effect if it is loaded or loading. */
  ensureLoaded(): void {
    if (!this.loaded() && !this.loading()) {
      this.load();
    }
  }

  /** Reloads the list (the most recent response wins). */
  load(): void {
    const id = ++this.requestId;
    this.loading.set(true);
    this.error.set(null);
    // The error is shown by the screen (error state with Retry), not in a toast.
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    this.api.listJobTitles('body', false, { context }).subscribe({
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

  /** RG-09-05: creation. The list is reloaded to follow the back end's order (RG-09-08). */
  create(request: CreateJobTitleRequest): Observable<JobTitleResponse> {
    return this.api.createJobTitle(request).pipe(tap(() => this.load()));
  }

  /** Update: the label may change the order, so the list is reloaded (RG-09-08). */
  update(id: number, request: UpdateJobTitleRequest): Observable<JobTitleResponse> {
    return this.api.updateJobTitle(id, request).pipe(tap(() => this.load()));
  }

  /** RG-09-06: deactivation (never deletion). The order does not change: replaced in place. */
  deactivate(id: number): Observable<JobTitleResponse> {
    return this.pending.guard(id, () => this.api.deactivateJobTitle(id).pipe(tap((item) => this.replace(item))));
  }

  reactivate(id: number): Observable<JobTitleResponse> {
    return this.pending.guard(id, () => this.api.reactivateJobTitle(id).pipe(tap((item) => this.replace(item))));
  }

  private replace(item: JobTitleResponse): void {
    this.items.update((list) => list.map((current) => (current.id === item.id ? item : current)));
  }
}
