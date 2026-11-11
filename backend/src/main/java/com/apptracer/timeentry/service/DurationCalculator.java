package com.apptracer.timeentry.service;

import com.apptracer.timeentry.domain.Granularity;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * RG-01-08: pure computation of a time entry's duration, in minutes. The standard day is passed as
 * a parameter; the input values are assumed to be already validated (RG-01-01 to RG-01-03).
 */
public final class DurationCalculator {

  /**
   * @param endDate used for RANGE only
   * @param startTime used for HOUR only
   * @param endTime used for HOUR only
   * @param standardDayMinutes standard day in force (RG-00-02)
   */
  public int compute(
      Granularity granularity,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      LocalTime endTime,
      int standardDayMinutes) {
    return switch (granularity) {
      // RG-01-08: HOUR = gap in minutes
      case HOUR -> (int) Duration.between(startTime, endTime).toMinutes();
      // RG-01-08: DAY = 1 standard day, weekends included
      case DAY -> standardDayMinutes;
      // RG-01-08: RANGE = working days (Monday-Friday) x standard day, 0 without a working day
      case RANGE -> countWeekdays(startDate, endDate) * standardDayMinutes;
    };
  }

  /**
   * Number of days from Monday to Friday between the two dates, both included (public holidays
   * counted).
   */
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
