// RG-00-01 : le fuseau horaire métier est Europe/Paris. `DatePipe` n'accepte pas de nom de fuseau IANA, on passe par Intl.
const DATE = new Intl.DateTimeFormat('fr-FR', {
  timeZone: 'Europe/Paris',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});
const TIME = new Intl.DateTimeFormat('fr-FR', {
  timeZone: 'Europe/Paris',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/**
 * ISO 8601 instant in Paris time: date '12/10/2026' and time '14:05', identical in both languages (RG-08-08).
 * The word linking them ('à' / 'at') belongs to the dictionary. Null if the date is unreadable.
 */
export function parisDateTimeParts(iso: string): { date: string; time: string } | null {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return { date: DATE.format(date), time: TIME.format(date) };
}
