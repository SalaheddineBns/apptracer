package com.apptracer.timeentry.service;

import com.apptracer.timeentry.domain.TimeEntry;
import java.time.LocalDate;
import java.util.List;

/** Semaine de saisies d'un compte avec son total (RG-01-13). */
public record WeeklyTimeEntries(
    LocalDate weekStart, LocalDate weekEnd, int totalMinutes, List<TimeEntry> entries) {}
