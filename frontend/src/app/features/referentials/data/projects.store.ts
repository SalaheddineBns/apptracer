import { HttpContext } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import {
  ProjectResponse,
  ProjectsService,
  CreateProjectRequest,
  UpdateProjectRequest,
} from '../../../core/api/generated';
import { SKIP_ERROR_TOAST } from '../../../core/errors/error.interceptor';
import { toProblem } from '../../../core/errors/problem';
import { PendingIds } from '../../../shared/pending-ids';

/**
 * Projects (SFD-02). RG-02-09: the back end returns only the active ones for USER and MANAGER, all for ADMIN.
 * RG-02-10: the order returned (alphabetical by name) is kept; it is never re-sorted here.
 */
@Injectable({ providedIn: 'root' })
export class ProjectsStore {
  private readonly api = inject(ProjectsService);
  private requestId = 0;

  /** Deactivations / reactivations waiting for a response: one request at a time per row. */
  readonly pending = new PendingIds();

  readonly items = signal<ProjectResponse[]>([]);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);
  /** RG-02-06: only active projects are offered for a time entry (same order as the list). */
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
    this.api.listProjects('body', false, { context }).subscribe({
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

  /** RG-02-01, RG-02-08: creation. The list is reloaded to follow the back end's order (RG-02-10). */
  create(request: CreateProjectRequest): Observable<ProjectResponse> {
    return this.api.createProject(request).pipe(tap(() => this.load()));
  }

  /** Update: the name may change the order, so the list is reloaded (RG-02-10). */
  update(id: number, request: UpdateProjectRequest): Observable<ProjectResponse> {
    return this.api.updateProject(id, request).pipe(tap(() => this.load()));
  }

  /** RG-02-05: deactivation (never deletion). The order does not change: replaced in place. */
  deactivate(id: number): Observable<ProjectResponse> {
    return this.pending.guard(id, () => this.api.deactivateProject(id).pipe(tap((item) => this.replace(item))));
  }

  reactivate(id: number): Observable<ProjectResponse> {
    return this.pending.guard(id, () => this.api.reactivateProject(id).pipe(tap((item) => this.replace(item))));
  }

  private replace(item: ProjectResponse): void {
    this.items.update((list) => list.map((current) => (current.id === item.id ? item : current)));
  }
}
