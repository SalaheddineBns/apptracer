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
 * Instant ISO 8601 en heure de Paris : date « 12/10/2026 » et heure « 14:05 », identiques dans les deux langues (RG-08-08).
 * Le mot qui les relie (« à » / « at ») appartient au dictionnaire. Null si la date est illisible.
 */
export function parisDateTimeParts(iso: string): { date: string; time: string } | null {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return { date: DATE.format(date), time: TIME.format(date) };
}
