import {
  addDays,
  countWeekdays,
  daysBetween,
  formatDayMonth,
  formatDate,
  formatWeekdayName,
  isIsoDate,
  isoToLocalDate,
  isWeekday,
  localDateToIso,
  mondayOf,
  todayInParis,
  weekDays,
} from './week';

describe('shared/dates/week', () => {
  it('SFD-01 E1 : la semaine va du lundi au dimanche', () => {
    expect(mondayOf('2026-10-05')).toBe('2026-10-05');
    expect(mondayOf('2026-10-08')).toBe('2026-10-05');
    expect(mondayOf('2026-10-11')).toBe('2026-10-05');
    expect(mondayOf('2026-10-12')).toBe('2026-10-12');
    expect(weekDays('2026-10-05')).toEqual([
      '2026-10-05',
      '2026-10-06',
      '2026-10-07',
      '2026-10-08',
      '2026-10-09',
      '2026-10-10',
      '2026-10-11',
    ]);
  });

  it('les calculs traversent un changement de mois, d’année et d’heure sans décalage', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01');
    expect(addDays('2026-01-01', -1)).toBe('2025-12-31');
    // Switch to winter time in Paris on 25/10/2026.
    expect(addDays('2026-10-24', 2)).toBe('2026-10-26');
    expect(daysBetween('2026-10-05', '2026-11-04')).toBe(30);
    expect(daysBetween('2026-10-05', '2026-10-04')).toBe(-1);
  });

  it('RG-01-08 : les jours ouvrés vont du lundi au vendredi', () => {
    expect(isWeekday('2026-10-09')).toBe(true);
    expect(isWeekday('2026-10-10')).toBe(false);
    expect(isWeekday('2026-10-11')).toBe(false);
  });

  it('CA-01-03 : du lundi 05/10/2026 au vendredi 16/10/2026, 10 jours ouvrés', () => {
    expect(countWeekdays('2026-10-05', '2026-10-16')).toBe(10);
  });

  it('RG-01-08 : une plage sans jour ouvré en compte 0', () => {
    expect(countWeekdays('2026-10-10', '2026-10-11')).toBe(0);
    expect(countWeekdays('2026-10-06', '2026-10-05')).toBe(0);
  });

  it('RG-00-01 : la date du jour est celle de Paris, pas celle du navigateur', () => {
    // 23:30 UTC on 04/10: already 05/10 in Paris (UTC+2).
    expect(todayInParis(new Date('2026-10-04T23:30:00Z'))).toBe('2026-10-05');
    expect(todayInParis(new Date('2026-10-05T10:00:00Z'))).toBe('2026-10-05');
  });

  it('refuse ce qui n’est pas un jour civil', () => {
    expect(isIsoDate('2026-10-05')).toBe(true);
    expect(isIsoDate('2026-02-31')).toBe(false);
    expect(isIsoDate('05/10/2026')).toBe(false);
    expect(isIsoDate(null)).toBe(false);
  });

  it('formats français', () => {
    expect(formatDate('2026-10-05')).toBe('05/10/2026');
    expect(formatDayMonth('2026-10-05')).toBe('05/10');
    expect(formatWeekdayName('2026-10-05')).toBe('lundi');
    expect(formatWeekdayName('2026-10-11')).toBe('dimanche');
  });

  it('aller-retour entre jour civil et date locale du sélecteur', () => {
    expect(localDateToIso(isoToLocalDate('2026-10-05'))).toBe('2026-10-05');
    expect(localDateToIso(new Date(2026, 0, 1))).toBe('2026-01-01');
  });
});
