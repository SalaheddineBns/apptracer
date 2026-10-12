import { Validators, ValidatorFn } from '@angular/forms';

/** RG-02-01 : majuscules, chiffres et `_`, de 2 à 30 caractères (le back reste la référence). */
export const REFERENTIAL_CODE_PATTERN = /^[A-Z0-9_]{2,30}$/;

/** Validateur du code d'un type ou d'un projet (RG-02-01) ; la saisie n'est jamais transformée. */
export const referentialCodeValidators: ValidatorFn[] = [Validators.required, Validators.pattern(REFERENTIAL_CODE_PATTERN)];

export const REFERENTIAL_CODE_MESSAGES: Record<string, string> = {
  required: 'Le code est obligatoire.',
  pattern: 'Le code doit contenir de 2 à 30 caractères : majuscules, chiffres et _ (ex. REUNION_CLIENT).',
};
