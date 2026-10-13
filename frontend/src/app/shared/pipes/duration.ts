import { Pipe, PipeTransform } from '@angular/core';

const DECIMAL_HOURS = new Intl.NumberFormat('fr-FR', {
  maximumFractionDigits: 2,
  useGrouping: false,
});

/** Durée d'une saisie en heures et minutes : « 1 h 30 », « 7 h 00 » (design system). */
export function formatDuration(totalMinutes: number): string {
  const hours = Math.floor(totalMinutes / 60);
  return `${hours} h ${String(totalMinutes % 60).padStart(2, '0')}`;
}

/** Total en heures décimales : « 8,5 h » (design system, CA-01-09). */
export function formatDecimalHours(totalMinutes: number): string {
  return `${DECIMAL_HOURS.format(totalMinutes / 60)} h`;
}

@Pipe({ name: 'duration' })
export class DurationPipe implements PipeTransform {
  transform(totalMinutes: number): string {
    return formatDuration(totalMinutes);
  }
}

@Pipe({ name: 'decimalHours' })
export class DecimalHoursPipe implements PipeTransform {
  transform(totalMinutes: number): string {
    return formatDecimalHours(totalMinutes);
  }
}
