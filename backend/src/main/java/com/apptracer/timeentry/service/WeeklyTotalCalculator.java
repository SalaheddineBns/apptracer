package com.apptracer.timeentry.service;

import com.apptracer.timeentry.domain.Granularity;
import com.apptracer.timeentry.domain.TimeEntry;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * RG-01-13, RG-04-05: duration of each of the seven days of a week, and the week total (their sum).
 * Pure component: it receives non-deleted time entries and reads neither settings nor the database.
 */
public final class WeeklyTotalCalculator {

  /**
   * @param minutesPerDay duration of each day, Monday (index 0) to Sunday (index 6)
   * @param totalMinutes sum of the seven days
   */
  public record WeekTotals(List<Integer> minutesPerDay, int totalMinutes) {}

  public WeekTotals compute(List<TimeEntry> entries, LocalDate weekStart) {
    int[] days = new int[7];
    for (TimeEntry e : entries) {
      if (e.getGranularity() == Granularity.RANGE) {
        // RG-01-13, RG-01-08: working days of the range within the week x the standard day frozen
        // on the entry (not the setting in force)
        for (int i = 0; i < 7; i++) {
          LocalDate day = weekStart.plusDays(i);
          if (!day.isBefore(e.getStartDate()) && !day.isAfter(e.getEndDate()) && isWeekday(day)) {
            days[i] += e.getStandardDayMinutes();
          }
        }
      } else {
        // HOUR, DAY: the entry's frozen duration, counted on its date
        int index = (int) java.time.temporal.ChronoUnit.DAYS.between(weekStart, e.getStartDate());
        if (index >= 0 && index < 7) {
          days[index] += e.getDurationMinutes();
        }
      }
    }
    int total = 0;
    for (int d : days) {
      total += d;
    }
    return new WeekTotals(java.util.Arrays.stream(days).boxed().toList(), total);
  }

  private static boolean isWeekday(LocalDate d) {
    return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
  }
}
