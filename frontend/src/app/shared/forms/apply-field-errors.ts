import { AbstractControl, FormGroup } from '@angular/forms';
import { FieldError } from '../../core/errors/problem';

export const SERVER_ERROR_KEY = 'server';

/**
 * Pose chaque `errors[]` de l'API sur le contrôle du même nom (erreur `server`, affichée par `app-field-error`).
 * Retourne les erreurs sans contrôle correspondant.
 */
export function applyFieldErrors(form: FormGroup, errors: readonly FieldError[]): FieldError[] {
  const unmatched: FieldError[] = [];
  for (const error of errors) {
    const control: AbstractControl | null = form.get(error.field);
    if (control) {
      control.setErrors({ [SERVER_ERROR_KEY]: error.message });
      control.markAsTouched();
    } else {
      unmatched.push(error);
    }
  }
  return unmatched;
}
