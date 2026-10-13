package com.apptracer.timeentry.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** Journal d'une semaine, du lundi au dimanche (RG-01-13). */
public record WeeklyTimeEntriesResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate weekStart,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate weekEnd,
    // RG-01-13 : une PLAGE ne compte que ses jours ouvrés de la semaine x journée standard figée
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalMinutes,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TimeEntryResponse> entries) {}
