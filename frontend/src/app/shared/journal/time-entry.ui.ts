import { Granularity, TimeEntryResponse } from '../../core/api/generated';
import { formatDayMonth } from '../dates/week';

/** Période d'une saisie telle qu'affichée dans le journal : créneau, journée entière ou plage de jours. */
export function periodLabel(entry: TimeEntryResponse): string {
  switch (entry.granularity) {
    case Granularity.Hour:
      return `${entry.startTime ?? ''} – ${entry.endTime ?? ''}`;
    case Granularity.Range:
      return `Du ${formatDayMonth(entry.startDate)} au ${formatDayMonth(entry.endDate ?? entry.startDate)}`;
    default:
      return 'Journée entière';
  }
}

/** Jour(s) d'une saisie pour la nommer dans une phrase : « du 05/10 » ou « du 05/10 au 16/10 ». */
export function datesLabel(entry: TimeEntryResponse): string {
  const start = `du ${formatDayMonth(entry.startDate)}`;
  return entry.granularity === Granularity.Range && entry.endDate
    ? `${start} au ${formatDayMonth(entry.endDate)}`
    : start;
}

/** RG-01-13 : une PLAGE couvre chaque jour de sa date de début à sa date de fin ; les autres saisies, leur seul jour. */
export function coversDay(entry: TimeEntryResponse, day: string): boolean {
  if (entry.granularity === Granularity.Range && entry.endDate) {
    return entry.startDate <= day && day <= entry.endDate;
  }
  return entry.startDate === day;
}
