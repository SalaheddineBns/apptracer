package com.apptracer.timeentry.service;

import com.apptracer.timeentry.domain.TimeEntry;
import java.time.LocalDate;
import java.util.List;

/**
 * Week of time entries of an account, with its total (RG-01-13) and the duration of each day
 * (RG-07-02).
 *
 * @param minutesPerDay duration of each day, Monday (index 0) to Sunday (index 6), from {@link
 *     WeeklyTotalCalculator}
 */
public record WeeklyTimeEntries(
    LocalDate weekStart,
    LocalDate weekEnd,
    int totalMinutes,
    List<Integer> minutesPerDay,
    List<TimeEntry> entries) {}
