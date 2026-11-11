import { HttpContext } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { MyProjectsResponse, ProjectsService } from '../../../core/api/generated';
import { SKIP_ERROR_TOAST } from '../../../core/errors/error.interceptor';
import { toProblem } from '../../../core/errors/problem';

/**
 * RG-10-11: "My projects" of the signed-in account. The order sent by the back end (teams, then projects, by name) is
 * kept as is. Not shared between accounts: the page provides it and reloads it each time it opens.
 */
@Injectable()
export class MyProjectsStore {
  private readonly api = inject(ProjectsService);
  private requestId = 0;

  readonly data = signal<MyProjectsResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Reloads the projects (the most recent response wins). */
  load(): void {
    const id = ++this.requestId;
    this.loading.set(true);
    this.error.set(null);
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    this.api.listMyProjects('body', false, { context }).subscribe({
      next: (data) => {
        if (id !== this.requestId) {
          return;
        }
        this.data.set(data);
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
}
