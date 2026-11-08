import { Validators, ValidatorFn } from '@angular/forms';
import type { FieldMessages } from '../components/field-error';

/** RG-02-01 : majuscules, chiffres et `_`, de 2 à 30 caractères (le back reste la référence). */
export const REFERENTIAL_CODE_PATTERN = /^[A-Z0-9_]{2,30}$/;

/** Validateur du code d'un type ou d'un projet (RG-02-01) ; la saisie n'est jamais transformée. */
export const referentialCodeValidators: ValidatorFn[] = [Validators.required, Validators.pattern(REFERENTIAL_CODE_PATTERN)];

/** Messages d'erreur du code ; l'exemple est celui que l'aide du même champ cite (aide et erreur concordent). */
export function referentialCodeMessages(example: string): FieldMessages {
  return {
    required: 'referentialCode.required',
    pattern: { key: 'referentialCode.pattern', params: { example } },
  };
}
