package com.apptracer.timeentry.service;

import com.apptracer.timeentry.domain.Granularity;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * RG-01-08 : calcul pur de la durée d'une saisie, en minutes. La journée standard est reçue en
 * paramètre ; les valeurs d'entrée sont supposées déjà validées (RG-01-01 à RG-01-03).
 */
public final class DurationCalculator {

  /**
   * @param endDate utilisé seulement pour RANGE
   * @param startTime utilisé seulement pour HOUR
   * @param endTime utilisé seulement pour HOUR
   * @param standardDayMinutes journée standard en vigueur (RG-00-02)
   */
  public int compute(
      Granularity granularity,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      LocalTime endTime,
      int standardDayMinutes) {
    return switch (granularity) {
      // RG-01-08 : HEURE = écart en minutes
      case HOUR -> (int) Duration.between(startTime, endTime).toMinutes();
      // RG-01-08 : JOURNEE = 1 journée standard, week-end compris
      case DAY -> standardDayMinutes;
      // RG-01-08 : PLAGE = jours ouvrés (lundi-vendredi) x journée standard, 0 sans jour ouvré
      case RANGE -> countWeekdays(startDate, endDate) * standardDayMinutes;
    };
  }

  /** Nombre de jours du lundi au vendredi entre les deux dates incluses (fériés comptés). */
  static int countWeekdays(LocalDate from, LocalDate to) {
    int count = 0;
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      DayOfWeek dow = d.getDayOfWeek();
      if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
        count++;
      }
    }
    return count;
  }
}
