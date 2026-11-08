import { Granularity, TimeEntryResponse } from '../../core/api/generated';
import { I18n } from '../../core/i18n/i18n.service';
import { formatDayMonth } from '../dates/week';

/** Période d'une saisie telle qu'affichée dans le journal : créneau, journée entière ou plage de jours. */
export function periodLabel(entry: TimeEntryResponse, i18n: I18n): string {
  switch (entry.granularity) {
    case Granularity.Hour:
      return `${entry.startTime ?? ''} – ${entry.endTime ?? ''}`;
    case Granularity.Range:
      return i18n.t('timeEntry.range', {
        start: formatDayMonth(entry.startDate),
        end: formatDayMonth(entry.endDate ?? entry.startDate),
      });
    default:
      return i18n.t('timeEntry.allDay');
  }
}

/** Jour(s) d'une saisie pour la nommer dans une phrase : « du 05/10 » ou « du 05/10 au 16/10 » (« on 05/10 », « from 05/10 to 16/10 » en anglais). */
export function datesLabel(entry: TimeEntryResponse, i18n: I18n): string {
  return entry.granularity === Granularity.Range && entry.endDate
    ? i18n.t('timeEntry.fromTo', { start: formatDayMonth(entry.startDate), end: formatDayMonth(entry.endDate) })
    : i18n.t('timeEntry.on', { date: formatDayMonth(entry.startDate) });
}

/** RG-01-13 : une PLAGE couvre chaque jour de sa date de début à sa date de fin ; les autres saisies, leur seul jour. */
export function coversDay(entry: TimeEntryResponse, day: string): boolean {
  if (entry.granularity === Granularity.Range && entry.endDate) {
    return entry.startDate <= day && day <= entry.endDate;
  }
  return entry.startDate === day;
}
