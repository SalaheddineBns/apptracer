package com.apptracer.referential.api;

import com.apptracer.referential.domain.TagColor;
import io.swagger.v3.oas.annotations.media.Schema;

/** Type d'activité (RG-02-04 pour la couleur). */
public record ActivityTypeResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean projectRequired,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) TagColor color,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {}
