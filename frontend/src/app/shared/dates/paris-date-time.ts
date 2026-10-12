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

/** Instant ISO 8601 affiché en heure de Paris : « 12/10/2026 à 14:05 ». Chaîne vide si la date est illisible. */
export function formatParisDateTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  return `${DATE.format(date)} à ${TIME.format(date)}`;
}
