package com.apptracer.timeentry.api;

import com.apptracer.referential.domain.TagColor;
import io.swagger.v3.oas.annotations.media.Schema;

/** Activity type embedded in a time entry, displayable even when deactivated (RG-02-05). */
public record ActivityTypeSummary(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) TagColor color,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {}
