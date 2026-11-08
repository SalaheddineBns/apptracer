import { HttpContext } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { CreateJobTitleRequest, JobTitleResponse, JobTitlesService, UpdateJobTitleRequest } from '../../../core/api/generated';
import { SKIP_ERROR_TOAST } from '../../../core/errors/error.interceptor';
import { toProblem } from '../../../core/errors/problem';
import { PendingIds } from '../../../shared/pending-ids';

/**
 * Fonctions des collaborateurs (SFD-09). RG-09-07 : le back renvoie les actives seules pour USER et MANAGER, toutes pour ADMIN.
 * RG-09-08 : l'ordre renvoyé (libellé alphabétique) fait foi, il n'est jamais retrié ici.
 */
@Injectable({ providedIn: 'root' })
export class JobTitlesStore {
  private readonly api = inject(JobTitlesService);
  private requestId = 0;

  /** Désactivations / réactivations en attente de réponse : une seule requête à la fois par ligne. */
  readonly pending = new PendingIds();

  readonly items = signal<JobTitleResponse[]>([]);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);
  /** RG-09-06 : seules les fonctions actives sont proposées pour l'attribution (même ordre que la liste). */
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

  /** RG-09-05 : création. La liste est rechargée pour respecter l'ordre du back (RG-09-08). */
  create(request: CreateJobTitleRequest): Observable<JobTitleResponse> {
    return this.api.createJobTitle(request).pipe(tap(() => this.load()));
  }

  /** Modification : le libellé peut changer l'ordre, la liste est rechargée (RG-09-08). */
  update(id: number, request: UpdateJobTitleRequest): Observable<JobTitleResponse> {
    return this.api.updateJobTitle(id, request).pipe(tap(() => this.load()));
  }

  /** RG-09-06 : désactivation (jamais de suppression). L'ordre ne change pas : remplacement en place. */
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
