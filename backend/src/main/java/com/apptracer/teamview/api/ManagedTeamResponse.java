package com.apptracer.teamview.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Team the user manages. */
public record ManagedTeamResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {}
