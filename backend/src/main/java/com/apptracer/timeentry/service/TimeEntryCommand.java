package com.apptracer.timeentry.service;

import com.apptracer.timeentry.domain.Granularity;
import java.time.LocalDate;
import java.time.LocalTime;

/** Données d'une saisie à enregistrer, telles que reçues de l'API (type et projet par code). */
public record TimeEntryCommand(
    Granularity granularity,
    LocalDate startDate,
    LocalDate endDate,
    LocalTime startTime,
    LocalTime endTime,
    String activityTypeCode,
    String projectCode,
    String comment) {}
