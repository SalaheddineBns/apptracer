import { AbstractControl, ValidationErrors } from '@angular/forms';

export interface HoursMinutes {
  readonly hours: number | null;
  readonly minutes: number | null;
}

/** Entered hours and minutes converted to minutes; null if either value is missing. */
export function toMinutes({ hours, minutes }: HoursMinutes): number | null {
  return hours === null || minutes === null ? null : hours * 60 + minutes;
}

export function fromMinutes(total: number): { hours: number; minutes: number } {
  return { hours: Math.floor(total / 60), minutes: total % 60 };
}

/** RG-06-02: from 60 to 1,440 minutes, in steps of 15 (the back end remains the reference). */
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

/** '7 h 30', '7 h 00' (two-digit minutes, as in the design system). */
export function formatDuration(total: number): string {
  const { hours, minutes } = fromMinutes(total);
  return `${hours} h ${String(minutes).padStart(2, '0')}`;
}
