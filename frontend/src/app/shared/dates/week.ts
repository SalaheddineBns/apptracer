import { INTL_LOCALE, Lang } from '../../core/i18n/lang';

/**
 * Dates de saisie : toujours des jours civils `YYYY-MM-DD`, sans heure ni fuseau (comme le `LocalDate` du back).
 * Les calculs passent par UTC pour qu'un changement d'heure ne décale jamais un jour.
 */
const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;
const DAY_MS = 86_400_000;

// RG-00-01 : la date du jour est celle de Paris, quel que soit le fuseau du navigateur.
const PARIS_TODAY = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Europe/Paris',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});
const WEEKDAY: Record<Lang, Intl.DateTimeFormat> = {
  fr: new Intl.DateTimeFormat(INTL_LOCALE.fr, { timeZone: 'UTC', weekday: 'long' }),
  en: new Intl.DateTimeFormat(INTL_LOCALE.en, { timeZone: 'UTC', weekday: 'long' }),
};

function toUtc(iso: string): number | null {
  const match = ISO_DATE.exec(iso);
  if (!match) {
    return null;
  }
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const time = Date.UTC(year, month - 1, day);
  const date = new Date(time);
  // Refuse les dates impossibles (2026-02-31), que Date.UTC reporterait sur le mois suivant.
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
    ? time
    : null;
}

function fromUtc(time: number): string {
  return new Date(time).toISOString().slice(0, 10);
}

export function isIsoDate(value: unknown): value is string {
  return typeof value === 'string' && toUtc(value) !== null;
}

/** RG-00-01 : date du jour à Paris. */
export function todayInParis(now: Date = new Date()): string {
  return PARIS_TODAY.format(now);
}

export function addDays(iso: string, days: number): string {
  return fromUtc((toUtc(iso) ?? 0) + days * DAY_MS);
}

/** Nombre de jours de `from` à `to` (négatif si `to` précède `from`). */
export function daysBetween(from: string, to: string): number {
  return Math.round(((toUtc(to) ?? 0) - (toUtc(from) ?? 0)) / DAY_MS);
}

/** 0 = lundi … 6 = dimanche. */
function weekdayIndex(iso: string): number {
  return (new Date(toUtc(iso) ?? 0).getUTCDay() + 6) % 7;
}

/** SFD-01 E1 : lundi de la semaine (lundi → dimanche) qui contient la date. */
export function mondayOf(iso: string): string {
  return addDays(iso, -weekdayIndex(iso));
}

/** Les sept jours de la semaine, du lundi au dimanche. */
export function weekDays(weekStart: string): string[] {
  return Array.from({ length: 7 }, (_, index) => addDays(weekStart, index));
}

/** RG-01-08 : jour ouvré = du lundi au vendredi (les jours fériés comptent comme ouvrés en v1). */
export function isWeekday(iso: string): boolean {
  return weekdayIndex(iso) < 5;
}

/** RG-01-08 : nombre de jours ouvrés de `start` à `end`, bornes incluses (0 si `end` précède `start`). */
export function countWeekdays(start: string, end: string): number {
  let count = 0;
  for (let day = start; daysBetween(day, end) >= 0; day = addDays(day, 1)) {
    if (isWeekday(day)) {
      count++;
    }
  }
  return count;
}

/** « 05/10/2026 » : jour/mois/année dans les deux langues (RG-08-08). */
export function formatDate(iso: string): string {
  const [year, month, day] = iso.split('-');
  return `${day}/${month}/${year}`;
}

/** « 05/10 ». */
export function formatDayMonth(iso: string): string {
  const [, month, day] = iso.split('-');
  return `${day}/${month}`;
}

/** « lundi » / « Monday » (RG-08-08). */
export function formatWeekdayName(iso: string, lang: Lang = 'fr'): string {
  return WEEKDAY[lang].format(new Date(toUtc(iso) ?? 0));
}

/** Date locale d'un `p-datepicker` (minuit, fuseau du navigateur) vers jour civil. */
export function localDateToIso(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Jour civil vers date locale pour un `p-datepicker`. */
export function isoToLocalDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year, month - 1, day);
}
