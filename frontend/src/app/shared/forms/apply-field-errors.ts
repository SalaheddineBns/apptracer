import { AbstractControl, FormGroup } from '@angular/forms';
import { FieldError } from '../../core/errors/problem';

export const SERVER_ERROR_KEY = 'server';

/**
 * Puts each of the API's `errors[]` on the control of the same name (`server` error, shown by `app-field-error`).
 * Returns the errors that have no matching control.
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
