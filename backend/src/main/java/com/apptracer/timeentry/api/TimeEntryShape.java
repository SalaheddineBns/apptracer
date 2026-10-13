package com.apptracer.timeentry.api;

import com.apptracer.timeentry.domain.Granularity;
import java.time.LocalDate;
import java.time.LocalTime;

/** Champs de période partagés par les requêtes de création et de modification. */
interface TimeEntryShape {

  Granularity granularity();

  LocalDate endDate();

  LocalTime startTime();

  LocalTime endTime();
}
