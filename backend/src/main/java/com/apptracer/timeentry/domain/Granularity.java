package com.apptracer.timeentry.domain;

/** Granularité d'une saisie (SFD-01 §2) : une heure, une journée ou une plage de jours. */
public enum Granularity {
  HOUR,
  DAY,
  RANGE
}
