package com.apptracer.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** Durée d'un jour en minutes (RG-04-05). */
public record DailyTotal(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate date,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int minutes) {}
