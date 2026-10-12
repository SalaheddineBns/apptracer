import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl } from '@angular/forms';
import { merge, of, switchMap } from 'rxjs';
import { SERVER_ERROR_KEY } from '../forms/apply-field-errors';

/**
 * Message d'erreur sous un champ, affiché après le premier blur (ou immédiatement pour une erreur serveur).
 * `messages` associe une clé d'erreur de validateur à son libellé.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-field-error',
  template: `
    @if (message(); as text) {
      <small class="text-xs text-red-700 dark:text-red-400" [attr.data-cy]="cy()" role="alert">{{ text }}</small>
    }
  `,
})
export class FieldError {
  readonly control = input.required<AbstractControl | null>();
  readonly messages = input<Record<string, string>>({});
  /** `data-cy` du message (par défaut `field-error`). */
  readonly cy = input('field-error');

  private readonly state = toSignal(
    toObservable(this.control).pipe(
      switchMap((control) => (control ? merge(of(control), control.events) : of(null))),
    ),
    { initialValue: null },
  );

  protected readonly message = computed<string | null>(() => {
    this.state();
    const control = this.control();
    const errors = control?.errors;
    if (!control || !errors) {
      return null;
    }
    const server = errors[SERVER_ERROR_KEY];
    if (typeof server === 'string') {
      return server;
    }
    if (!control.touched) {
      return null;
    }
    const key = Object.keys(errors).find((k) => k in this.messages());
    return key ? (this.messages()[key] ?? null) : null;
  });
}
