package com.apptracer.teamview.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Member as seen by their manager. RG-04-07: {@code active} flags an inactive account. */
public record TeamViewMember(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {}
