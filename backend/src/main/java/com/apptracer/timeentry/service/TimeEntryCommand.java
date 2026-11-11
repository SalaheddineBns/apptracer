package com.apptracer.timeentry.service;

import com.apptracer.timeentry.domain.Granularity;
import java.time.LocalDate;
import java.time.LocalTime;

/** Data of a time entry to save, as received from the API (type and project by code). */
public record TimeEntryCommand(
    Granularity granularity,
    LocalDate startDate,
    LocalDate endDate,
    LocalTime startTime,
    LocalTime endTime,
    String activityTypeCode,
    String projectCode,
    String comment) {}
