import { HttpContext } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { TeamResponse, TeamsService } from '../../../core/api/generated';
import { SKIP_ERROR_TOAST } from '../../../core/errors/error.interceptor';
import { toProblem } from '../../../core/errors/problem';

/**
 * Équipes (SFD-05, ADMIN). RG-05-13 : le back renvoie les équipes triées par nom et les membres triés par nom puis prénom,
 * l'ordre reçu fait foi et n'est jamais retrié ici.
 */
@Injectable({ providedIn: 'root' })
export class TeamsStore {
  private readonly api = inject(TeamsService);
  private requestId = 0;

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
}
