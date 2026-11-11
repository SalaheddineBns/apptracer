import { inject, Pipe, PipeTransform } from '@angular/core';
import { I18n } from '../../core/i18n/i18n.service';
import { INTL_LOCALE, Lang } from '../../core/i18n/lang';

// RG-08-08: comma in French, point in English.
const DECIMAL_HOURS: Record<Lang, Intl.NumberFormat> = {
  fr: new Intl.NumberFormat(INTL_LOCALE.fr, { maximumFractionDigits: 2, useGrouping: false }),
  en: new Intl.NumberFormat(INTL_LOCALE.en, { maximumFractionDigits: 2, useGrouping: false }),
};

/** Duration of a time entry in hours and minutes: '1 h 30', '7 h 00', identical in both languages (RG-08-08). */
export function formatDuration(totalMinutes: number): string {
  const hours = Math.floor(totalMinutes / 60);
  return `${hours} h ${String(totalMinutes % 60).padStart(2, '0')}`;
}

/** Total in decimal hours: '8,5 h' in French, '8.5 h' in English (CA-01-09, RG-08-08). */
export function formatDecimalHours(totalMinutes: number, lang: Lang = 'fr'): string {
  return `${DECIMAL_HOURS[lang].format(totalMinutes / 60)} h`;
}

@Pipe({ name: 'duration' })
export class DurationPipe implements PipeTransform {
  transform(totalMinutes: number): string {
    return formatDuration(totalMinutes);
  }
}

/** Impure: follows the language signal (RG-08-04). */
@Pipe({ name: 'decimalHours', pure: false })
export class DecimalHoursPipe implements PipeTransform {
  private readonly i18n = inject(I18n);

  transform(totalMinutes: number): string {
    return formatDecimalHours(totalMinutes, this.i18n.lang());
  }
}
