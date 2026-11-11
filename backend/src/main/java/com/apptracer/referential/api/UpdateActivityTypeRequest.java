package com.apptracer.referential.api;

import com.apptracer.referential.domain.TagColor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** RG-02-01: no {@code code}, which cannot change after creation. */
public record UpdateActivityTypeRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String label,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean projectRequired,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) @NotNull TagColor color,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long version) {}
