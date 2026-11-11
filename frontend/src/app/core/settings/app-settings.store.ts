import { HttpContext } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { MessageService } from 'primeng/api';
import { defer, EMPTY, finalize, Observable, tap } from 'rxjs';
import { AppSettingsResponse, AppSettingsService, SettingsAuthor } from '../api/generated';
import { SKIP_ERROR_TOAST } from '../errors/error.interceptor';
import { toProblem } from '../errors/problem';
import { I18n } from '../i18n/i18n.service';

/**
 * Application settings (SFD-06), read by every role (RG-06-03): the Settings screen (ADMIN) and the
 * duration computed live in the time entry dialog (SFD-01). RG-06-05: `lastModifiedAt` / `lastModifiedBy`
 * are filled in by the back end only for the ADMIN, and only if the value has already been changed.
 */
@Injectable({ providedIn: 'root' })
export class AppSettingsStore {
  private readonly api = inject(AppSettingsService);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18n);
  private requestId = 0;

  readonly settings = signal<AppSettingsResponse | null>(null);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);
  /** Change waiting for a response: one request at a time. */
  readonly saving = signal(false);

  /** RG-00-02: standard day in minutes, usable directly for a duration computation (null until loaded). */
  readonly standardDayMinutes = computed(() => this.settings()?.standardDayMinutes ?? null);
  /**
   * RG-06-05: null if never changed or if the caller is not an ADMIN (the back end returns null or omits the field).
   */
  readonly lastModifiedAt = computed<string | null>(() => this.settings()?.lastModifiedAt ?? null);
  readonly lastModifiedBy = computed<SettingsAuthor | null>(
    () => this.settings()?.lastModifiedBy ?? null,
  );

  /** Loads once: no effect if the value is loaded or loading. */
  ensureLoaded(): void {
    if (!this.loaded() && !this.loading()) {
      this.load();
    }
  }

  /** Forces a reload (the most recent response wins). */
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
   * RG-06-02, RG-06-03: change (ADMIN), with the version received. One request at a time: a call
   * while waiting, or before the first load, is ignored. Errors are passed on to the caller without a
   * toast for 400 (field) and 409 (concurrent change: the store then reloads to reflect the real state);
   * other errors (403, 5xx, network) are notified here with a toast, then passed on.
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
        summary: this.i18n.t('common.error'),
        detail: problem.detail,
        life: 5000,
      });
    }
  }
}
