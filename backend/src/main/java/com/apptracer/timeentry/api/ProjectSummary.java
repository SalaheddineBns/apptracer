package com.apptracer.timeentry.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Project embedded in a time entry, displayable even when deactivated (RG-02-05). */
public record ProjectSummary(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {}
