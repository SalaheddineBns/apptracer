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
 * Projets (SFD-02). RG-02-09 : le back renvoie les actifs seuls pour USER et MANAGER, tous pour ADMIN.
 * RG-02-10 : l'ordre renvoyé (nom alphabétique) fait foi, il n'est jamais retrié ici.
 */
@Injectable({ providedIn: 'root' })
export class ProjectsStore {
  private readonly api = inject(ProjectsService);
  private requestId = 0;

  /** Désactivations / réactivations en attente de réponse : une seule requête à la fois par ligne. */
  readonly pending = new PendingIds();

  readonly items = signal<ProjectResponse[]>([]);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);
  /** RG-02-06 : seuls les projets actifs sont proposés pour une saisie (même ordre que la liste). */
  readonly activeItems = computed(() => this.items().filter((item) => item.active));

  /** Charge la liste une seule fois : sans effet si elle est chargée ou en cours de chargement. */
  ensureLoaded(): void {
    if (!this.loaded() && !this.loading()) {
      this.load();
    }
  }

  /** Recharge la liste (la réponse la plus récente l'emporte). */
  load(): void {
    const id = ++this.requestId;
    this.loading.set(true);
    this.error.set(null);
    // L'erreur est affichée par l'écran (état d'erreur « Réessayer »), pas dans un toast.
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

  /** RG-02-01, RG-02-08 : création. La liste est rechargée pour respecter l'ordre du back (RG-02-10). */
  create(request: CreateProjectRequest): Observable<ProjectResponse> {
    return this.api.createProject(request).pipe(tap(() => this.load()));
  }

  /** Modification : le nom peut changer l'ordre, la liste est rechargée (RG-02-10). */
  update(id: number, request: UpdateProjectRequest): Observable<ProjectResponse> {
    return this.api.updateProject(id, request).pipe(tap(() => this.load()));
  }

  /** RG-02-05 : désactivation (jamais de suppression). L'ordre ne change pas : remplacement en place. */
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
