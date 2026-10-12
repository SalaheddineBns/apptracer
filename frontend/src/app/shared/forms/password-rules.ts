import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export const PASSWORD_MIN_LENGTH = 12;
// Libellés identiques à ceux du back (PasswordPolicy).
export const PASSWORD_MESSAGES: Record<string, string> = {
  required: 'Le mot de passe est obligatoire.',
  passwordLength: `Le mot de passe doit faire au moins ${PASSWORD_MIN_LENGTH} caractères.`,
  passwordLetter: 'Le mot de passe doit contenir au moins une lettre.',
  passwordDigit: 'Le mot de passe doit contenir au moins un chiffre.',
  passwordTooLong: 'Le mot de passe est trop long (72 octets au maximum).',
};

/** RG-03-02 : au moins 12 caractères, au moins une lettre et un chiffre (le back reste la référence). */
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

/** La valeur doit être identique à celle du contrôle frère `otherName`. */
export function sameAs(otherName: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const other = control.parent?.get(otherName);
    return other && control.value !== other.value ? { mismatch: true } : null;
  };
}
