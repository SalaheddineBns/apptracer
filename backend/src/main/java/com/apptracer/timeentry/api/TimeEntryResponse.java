package com.apptracer.timeentry.api;

import com.apptracer.timeentry.domain.Granularity;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;

public record TimeEntryResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) Granularity granularity,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate startDate,
    @Schema(nullable = true, description = "Renseignée seulement pour RANGE") LocalDate endDate,
    @Schema(type = "string", example = "09:00", nullable = true, description = "Seulement HOUR")
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,
    @Schema(type = "string", example = "10:30", nullable = true, description = "Seulement HOUR")
        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ActivityTypeSummary activityType,
    @Schema(nullable = true) ProjectSummary project,
    @Schema(nullable = true) String comment,
    // RG-01-08 : durée figée à l'enregistrement
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int durationMinutes,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {}
