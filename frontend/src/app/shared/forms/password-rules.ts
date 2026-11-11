import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import type { FieldMessages } from '../components/field-error';

export const PASSWORD_MIN_LENGTH = 12;
// Messages equivalent to the back end's (PasswordPolicy), in both languages.
export const PASSWORD_MESSAGES: FieldMessages = {
  required: 'password.errors.required',
  passwordLength: { key: 'password.errors.length', params: { min: PASSWORD_MIN_LENGTH } },
  passwordLetter: 'password.errors.letter',
  passwordDigit: 'password.errors.digit',
  passwordTooLong: 'password.errors.tooLong',
};

/** RG-03-02: at least 12 characters, at least one letter and one digit (the back end remains the reference). */
export const passwordPolicyValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value: unknown = control.value;
  if (typeof value !== 'string' || value === '') {
    return null; // `required` s'en charge
  }
  const errors: ValidationErrors = {};
  if (Array.from(value).length < PASSWORD_MIN_LENGTH) {
    errors['passwordLength'] = true;
  }
  if (!/\p{L}/u.test(value)) {
    errors['passwordLetter'] = true;
  }
  if (!/\p{Nd}/u.test(value)) {
    errors['passwordDigit'] = true;
  }
  if (new TextEncoder().encode(value).length > 72) {
    errors['passwordTooLong'] = true;
  }
  return Object.keys(errors).length > 0 ? errors : null;
};

/** The value must be identical to that of the sibling control `otherName`. */
export function sameAs(otherName: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const other = control.parent?.get(otherName);
    return other && control.value !== other.value ? { mismatch: true } : null;
  };
}
