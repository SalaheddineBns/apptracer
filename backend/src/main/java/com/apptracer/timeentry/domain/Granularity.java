package com.apptracer.timeentry.domain;

/** Granularity of a time entry (SFD-01 §2): one hour slot, one day or a range of days. */
public enum Granularity {
  HOUR,
  DAY,
  RANGE
}
