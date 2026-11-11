package com.apptracer.timeentry.api;

import com.apptracer.timeentry.domain.Granularity;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Update of a time entry (RG-01-12): same fields as the creation, plus the version read. The owner
 * remains the user of the token (RG-00-03).
 */
@ValidGranularityFields
public record UpdateTimeEntryRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) @NotNull
        Granularity granularity,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate startDate,
    @Schema(description = "Requis si RANGE, interdit sinon") LocalDate endDate,
    @Schema(
            type = "string",
            pattern = "^([01]\\d|2[0-3]):[0-5]\\d$",
            example = "09:00",
            description = "HH:mm, requis si HOUR, interdit sinon")
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,
    @Schema(
            type = "string",
            pattern = "^([01]\\d|2[0-3]):[0-5]\\d$",
            example = "10:30",
            description = "HH:mm, requis si HOUR, interdit sinon")
        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String activityTypeCode,
    @Schema(description = "Requis ou interdit selon le type d'activité (RG-01-05)")
        String projectCode,
    // RG-01-09
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long version,
    @Schema(maxLength = 500) @Size(max = 500, message = "{validation.comment.max}") String comment)
    implements TimeEntryShape {}
