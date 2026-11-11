import { Validators, ValidatorFn } from '@angular/forms';
import type { FieldMessages } from '../components/field-error';

/** RG-02-01: capitals, digits and `_`, 2 to 30 characters (the back end remains the reference). */
export const REFERENTIAL_CODE_PATTERN = /^[A-Z0-9_]{2,30}$/;

/** Validator of a type's or a project's code (RG-02-01); the input is never transformed. */
export const referentialCodeValidators: ValidatorFn[] = [Validators.required, Validators.pattern(REFERENTIAL_CODE_PATTERN)];

/** Error messages of the code; the example is the one quoted by the same field's help (help and error match). */
export function referentialCodeMessages(example: string): FieldMessages {
  return {
    required: 'referentialCode.required',
    pattern: { key: 'referentialCode.pattern', params: { example } },
  };
}
