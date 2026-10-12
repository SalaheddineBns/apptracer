import { AbstractControl, ValidationErrors } from '@angular/forms';

export const STANDARD_DAY_MESSAGE =
  'La journée standard doit être comprise entre 1 h et 24 h, par pas de 15 minutes.';

export interface HoursMinutes {
  readonly hours: number | null;
  readonly minutes: number | null;
}

/** Heures et minutes saisies vers minutes ; null si une des deux valeurs manque. */
export function toMinutes({ hours, minutes }: HoursMinutes): number | null {
  return hours === null || minutes === null ? null : hours * 60 + minutes;
}

export function fromMinutes(total: number): { hours: number; minutes: number } {
  return { hours: Math.floor(total / 60), minutes: total % 60 };
}

/** RG-06-02 : de 60 à 1 440 minutes, par pas de 15 (le back reste la référence). */
export function standardDayValidator(group: AbstractControl): ValidationErrors | null {
  const value = group.value as HoursMinutes;
  const total = toMinutes(value);
  const valid =
    total !== null &&
    Number.isInteger(value.hours) &&
    Number.isInteger(value.minutes) &&
    (value.minutes as number) >= 0 &&
    (value.minutes as number) < 60 &&
    total >= 60 &&
    total <= 1440 &&
    total % 15 === 0;
  return valid ? null : { standardDay: true };
}
