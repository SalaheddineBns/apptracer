import { HttpContext } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { MessageService } from 'primeng/api';
import { defer, EMPTY, finalize, Observable, tap } from 'rxjs';
import { AppSettingsResponse, AppSettingsService, SettingsAuthor } from '../api/generated';
import { SKIP_ERROR_TOAST } from '../errors/error.interceptor';
import { toProblem } from '../errors/problem';

/**
 * Paramètres de l'application (SFD-06), lus par tous les rôles (RG-06-03) : écran « Paramètres » (ADMIN) et
 * durée calculée en direct du dialogue de saisie (SFD-01). RG-06-05 : `lastModifiedAt` / `lastModifiedBy`
 * ne sont renseignés par le back que pour l'ADMIN, et seulement si la valeur a déjà été modifiée.
 */
@Injectable({ providedIn: 'root' })
export class AppSettingsStore {
  private readonly api = inject(AppSettingsService);
  private readonly messages = inject(MessageService);
  private requestId = 0;

  readonly settings = signal<AppSettingsResponse | null>(null);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);
  /** Modification en attente de réponse : une seule requête à la fois. */
  readonly saving = signal(false);

  /** RG-00-02 : journée standard en minutes, utilisable directement pour un calcul de durée (null tant que non chargée). */
  readonly standardDayMinutes = computed(() => this.settings()?.standardDayMinutes ?? null);
  /** RG-06-05 : null si jamais modifiée ou si l'appelant n'est pas ADMIN (le back renvoie null ou omet le champ). */
  readonly lastModifiedAt = computed<string | null>(() => this.settings()?.lastModifiedAt ?? null);
  readonly lastModifiedBy = computed<SettingsAuthor | null>(
    () => this.settings()?.lastModifiedBy ?? null,
  );

  /** Charge une seule fois : sans effet si la valeur est chargée ou en cours de chargement. */
  ensureLoaded(): void {
    if (!this.loaded() && !this.loading()) {
      this.load();
    }
  }

  /** Force un rechargement (la réponse la plus récente l'emporte). */
  load(): void {
    const id = ++this.requestId;
    this.loading.set(true);
    this.error.set(null);
    // L'erreur est affichée par l'appelant (état d'erreur « Réessayer »), pas dans un toast.
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    this.api.getAppSettings('body', false, { context }).subscribe({
      next: (response) => {
        if (id !== this.requestId) {
          return;
        }
        this.settings.set(response);
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

  /**
   * RG-06-02, RG-06-03 : modification (ADMIN), avec la version reçue. Une seule requête à la fois : un appel
   * pendant l'attente, ou avant le premier chargement, est ignoré. Les erreurs sont relayées à l'appelant sans
   * toast pour 400 (champ) et 409 (modification concurrente : le store se recharge alors pour porter l'état réel) ;
   * les autres erreurs (403, 5xx, réseau) sont notifiées ici par un toast, puis relayées.
   */
  update(standardDayMinutes: number): Observable<AppSettingsResponse> {
    return defer(() => {
      const current = this.settings();
      if (this.saving() || current === null) {
        return EMPTY;
      }
      this.saving.set(true);
      const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
      return this.api
        .updateAppSettings({ standardDayMinutes, version: current.version }, 'body', false, {
          context,
        })
        .pipe(
          tap((response) => {
            // Une lecture partie avant cette réponse ne doit pas la remplacer par une valeur plus ancienne.
            this.requestId++;
            this.settings.set(response);
            this.loaded.set(true);
            this.loading.set(false);
            this.error.set(null);
          }),
          tap({ error: (error: unknown) => this.handleUpdateError(error) }),
          finalize(() => this.saving.set(false)),
        );
    });
  }

  private handleUpdateError(error: unknown): void {
    const problem = toProblem(error);
    if (problem.status === 409 && problem.code === null && problem.fieldErrors.length === 0) {
      this.load();
    } else if (problem.fieldErrors.length === 0) {
      this.messages.add({
        severity: 'error',
        summary: 'Erreur',
        detail: problem.detail,
        life: 5000,
      });
    }
  }
}
