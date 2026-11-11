import { ChangeDetectionStrategy, Component, computed, effect, inject, input, untracked } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl } from '@angular/forms';
import { merge, of, switchMap } from 'rxjs';
import { I18n } from '../../core/i18n/i18n.service';
import { TranslationKey, TranslationParams } from '../../core/i18n/keys';
import { SERVER_ERROR_KEY } from '../forms/apply-field-errors';

/** A validator's message: a dictionary key, or a key with its parameters (RG-08-05). */
export type MessageSpec = TranslationKey | { readonly key: TranslationKey; readonly params: TranslationParams };
export type FieldMessages = Record<string, MessageSpec>;

/**
 * Error message under a field, shown after the first blur (or immediately for a server error).
 * `messages` maps a validator's error key to its message (a dictionary key, translated into the language in force).
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
  readonly messages = input<FieldMessages>({});
  private readonly i18n = inject(I18n);
  /** `data-cy` of the message (`field-error` by default). */
  readonly cy = input('field-error');

  private readonly state = toSignal(
    toObservable(this.control).pipe(
      switchMap((control) => (control ? merge(of(control), control.events) : of(null))),
    ),
    { initialValue: null },
  );

  constructor() {
    // RG-08-04 : un message venu du serveur est écrit dans la langue de la requête ; au changement de langue on l'efface
    // (il se retrouve traduit à la prochaine action de l'utilisateur) en recalculant la validité depuis les seuls validateurs.
    let previous = this.i18n.lang();
    effect(() => {
      const lang = this.i18n.lang();
      untracked(() => {
        const control = this.control();
        if (lang !== previous && control?.errors && SERVER_ERROR_KEY in control.errors) {
          control.updateValueAndValidity();
        }
        previous = lang;
      });
    });
  }

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
    const spec = key ? this.messages()[key] : undefined;
    if (spec === undefined) {
      return null;
    }
    return typeof spec === 'string' ? this.i18n.t(spec) : this.i18n.t(spec.key, spec.params);
  });
}
