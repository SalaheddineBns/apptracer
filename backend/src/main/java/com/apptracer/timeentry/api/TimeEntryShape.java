package com.apptracer.timeentry.api;

import com.apptracer.timeentry.domain.Granularity;
import java.time.LocalDate;
import java.time.LocalTime;

/** Period fields shared by the create and update requests. */
interface TimeEntryShape {

  Granularity granularity();

  LocalDate endDate();

  LocalTime startTime();

  LocalTime endTime();
}
