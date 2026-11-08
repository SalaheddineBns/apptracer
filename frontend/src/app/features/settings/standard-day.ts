import { AbstractControl, ValidationErrors } from '@angular/forms';

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

/** « 7 h 30 », « 7 h 00 » (minutes à deux chiffres, comme le design system). */
export function formatDuration(total: number): string {
  const { hours, minutes } = fromMinutes(total);
  return `${hours} h ${String(minutes).padStart(2, '0')}`;
}
