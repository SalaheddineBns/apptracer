package com.apptracer.team.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Reference to a team (identifier and name). */
public record TeamRef(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {}
