import { Granularity, TimeEntryResponse } from '../../core/api/generated';
import { I18n } from '../../core/i18n/i18n.service';
import { formatDayMonth } from '../dates/week';

/** Period of a time entry as shown in the journal: time slot, full day or range of days. */
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

/**
 * Day(s) of a time entry to name it in a sentence: 'du 05/10' or 'du 05/10 au 16/10' ('on 05/10', 'from 05/10 to 16/10' in English).
 */
export function datesLabel(entry: TimeEntryResponse, i18n: I18n): string {
  return entry.granularity === Granularity.Range && entry.endDate
    ? i18n.t('timeEntry.fromTo', { start: formatDayMonth(entry.startDate), end: formatDayMonth(entry.endDate) })
    : i18n.t('timeEntry.on', { date: formatDayMonth(entry.startDate) });
}

/** RG-01-13: a RANGE covers each day from its start date to its end date; other time entries, their single day. */
export function coversDay(entry: TimeEntryResponse, day: string): boolean {
  if (entry.granularity === Granularity.Range && entry.endDate) {
    return entry.startDate <= day && day <= entry.endDate;
  }
  return entry.startDate === day;
}
