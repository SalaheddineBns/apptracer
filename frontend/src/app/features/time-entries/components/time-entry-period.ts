import { AbstractControl, ValidationErrors } from '@angular/forms';
import { daysBetween, localDateToIso, todayInParis } from '../../../shared/dates/week';

/** RG-01-03 : une PLAGE fait au plus 31 jours calendaires, bornes incluses. */
export const MAX_RANGE_DAYS = 31;
/** RG-01-01 : pas des créneaux horaires, en minutes. */
export const TIME_STEP_MINUTES = 15;
/** RG-01-09 : longueur maximale du commentaire. */
export const COMMENT_MAX_LENGTH = 500;

export function minutesOfDay(time: Date): number {
  return time.getHours() * 60 + time.getMinutes();
}

/** Heure d'un `p-datepicker` vers le format `HH:mm` de l'API. */
export function toTimeString(time: Date): string {
  return `${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}`;
}

/** Heure `HH:mm` de l'API vers une date du jour pour un `p-datepicker` en mode heure seule. */
export function timeStringToDate(time: string): Date {
  const [hours, minutes] = time.split(':').map(Number);
  const date = new Date();
  date.setHours(hours, minutes, 0, 0);
  return date;
}

function sibling(control: AbstractControl, name: string): Date | null {
  const value: unknown = control.parent?.get(name)?.value;
  return value instanceof Date ? value : null;
}

/** RG-01-07 : la date de début ne peut pas être dans le futur (date du jour à Paris, RG-00-01). */
export function notFutureValidator(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;
  return value instanceof Date && localDateToIso(value) > todayInParis() ? { future: true } : null;
}

/** RG-01-01 : les heures se saisissent par pas de 15 minutes. */
export function quarterHourValidator(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;
  return value instanceof Date && minutesOfDay(value) % TIME_STEP_MINUTES !== 0
    ? { quarterHour: true }
    : null;
}

/** RG-01-01 : l'heure de fin est après l'heure de début (le même jour : pas de créneau jusqu'à minuit). */
export function endTimeAfterStartValidator(control: AbstractControl): ValidationErrors | null {
  const end: unknown = control.value;
  const start = sibling(control, 'startTime');
  return end instanceof Date && start && minutesOfDay(end) <= minutesOfDay(start)
    ? { endBeforeStart: true }
    : null;
}

/** RG-01-03 : la date de fin est après la date de début, et la plage fait au plus 31 jours bornes incluses. */
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
