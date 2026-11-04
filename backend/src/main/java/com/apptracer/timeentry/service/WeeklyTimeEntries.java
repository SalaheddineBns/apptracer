package com.apptracer.timeentry.service;

import com.apptracer.timeentry.domain.TimeEntry;
import java.time.LocalDate;
import java.util.List;

/**
 * Semaine de saisies d'un compte avec son total (RG-01-13) et la durée de chaque jour (RG-07-02).
 *
 * @param minutesPerDay durée de chaque jour, lundi (index 0) à dimanche (index 6), issue de {@link
 *     WeeklyTotalCalculator}
 */
public record WeeklyTimeEntries(
    LocalDate weekStart,
    LocalDate weekEnd,
    int totalMinutes,
    List<Integer> minutesPerDay,
    List<TimeEntry> entries) {}
