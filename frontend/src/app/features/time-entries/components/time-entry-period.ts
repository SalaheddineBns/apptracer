import { AbstractControl, ValidationErrors } from '@angular/forms';
import { Granularity } from '../../../core/api/generated';
import {
  countWeekdays,
  daysBetween,
  localDateToIso,
  todayInParis,
} from '../../../shared/dates/week';

/** RG-01-03: a RANGE lasts at most 31 calendar days, bounds included. */
export const MAX_RANGE_DAYS = 31;
/** RG-01-01: step of the time slots, in minutes. */
export const TIME_STEP_MINUTES = 15;
/** RG-01-09: maximum length of the comment. */
export const COMMENT_MAX_LENGTH = 500;

export function minutesOfDay(time: Date): number {
  return time.getHours() * 60 + time.getMinutes();
}

/** Time from a `p-datepicker` to the API's `HH:mm` format. */
export function toTimeString(time: Date): string {
  return `${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}`;
}

/** API time `HH:mm` to a date of the day for a time-only `p-datepicker`. */
export function timeStringToDate(time: string): Date {
  const [hours, minutes] = time.split(':').map(Number);
  const date = new Date();
  date.setHours(hours, minutes, 0, 0);
  return date;
}

/**
 * RG-01-01: time offered by an empty picker, aligned on the 15-minute step (rounded down to the quarter
 * hour). Without it, PrimeNG starts from the current time (14:07) and its arrows give 14:22, 14:37…
 */
export function quarterHourFloor(time: Date): Date {
  const date = new Date(time);
  date.setMinutes(Math.floor(date.getMinutes() / TIME_STEP_MINUTES) * TIME_STEP_MINUTES, 0, 0);
  return date;
}

function sibling(control: AbstractControl, name: string): Date | null {
  const value: unknown = control.parent?.get(name)?.value;
  return value instanceof Date ? value : null;
}

/** RG-01-07: the start date cannot be in the future (today in Paris, RG-00-01). */
export function notFutureValidator(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;
  return value instanceof Date && localDateToIso(value) > todayInParis() ? { future: true } : null;
}

/** RG-01-01: times are entered in steps of 15 minutes. */
export function quarterHourValidator(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;
  return value instanceof Date && minutesOfDay(value) % TIME_STEP_MINUTES !== 0
    ? { quarterHour: true }
    : null;
}

/** RG-01-01: the end time is after the start time (on the same day: no slot until midnight). */
export function endTimeAfterStartValidator(control: AbstractControl): ValidationErrors | null {
  const end: unknown = control.value;
  const start = sibling(control, 'startTime');
  return end instanceof Date && start && minutesOfDay(end) <= minutesOfDay(start)
    ? { endBeforeStart: true }
    : null;
}

/** RG-01-03: the end date is after the start date, and the range lasts at most 31 days, bounds included. */
export function endDateValidator(control: AbstractControl): ValidationErrors | null {
  const end: unknown = control.value;
  const start = sibling(control, 'startDate');
  if (!(end instanceof Date) || !start) {
    return null;
  }
  const days = daysBetween(localDateToIso(start), localDateToIso(end));
  if (days <= 0) {
    return { endDateNotAfter: true };
  }
  return days + 1 > MAX_RANGE_DAYS ? { rangeTooLong: true } : null;
}

/** Period values needed to compute the duration (those of the dialog's form). */
export interface PeriodValue {
  readonly granularity: Granularity;
  readonly startDate: Date | null;
  readonly endDate: Date | null;
  readonly startTime: Date | null;
  readonly endTime: Date | null;
}

/** Duration in minutes; `weekdays` is the number of working days of a RANGE, null for the other granularities. */
export interface LiveDuration {
  readonly minutes: number;
  readonly weekdays: number | null;
}

/**
 * RG-01-08: duration shown live, with the standard day in force (RG-00-02). HOUR = gap in minutes;
 * DAY = one standard day, weekends included; RANGE = working days × standard day (0 without a working day).
 * Null while the period is incomplete or invalid, or while the standard day is not known.
 * The back end recalculates and freezes the duration on save: it remains the reference.
 */
export function liveDuration(
  period: PeriodValue,
  standardDayMinutes: number | null,
): LiveDuration | null {
  if (period.granularity === Granularity.Hour) {
    if (!period.startTime || !period.endTime) {
      return null;
    }
    const [start, end] = [minutesOfDay(period.startTime), minutesOfDay(period.endTime)];
    const valid = end > start && start % TIME_STEP_MINUTES === 0 && end % TIME_STEP_MINUTES === 0;
    return valid ? { minutes: end - start, weekdays: null } : null;
  }
  if (standardDayMinutes === null || !period.startDate) {
    return null;
  }
  if (period.granularity === Granularity.Day) {
    return { minutes: standardDayMinutes, weekdays: null };
  }
  if (!period.endDate) {
    return null;
  }
  const [start, end] = [localDateToIso(period.startDate), localDateToIso(period.endDate)];
  const days = daysBetween(start, end);
  if (days <= 0 || days + 1 > MAX_RANGE_DAYS) {
    return null;
  }
  const weekdays = countWeekdays(start, end);
  return { minutes: weekdays * standardDayMinutes, weekdays };
}
