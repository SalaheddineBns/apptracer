package com.apptracer.referential.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** RG-09-05: no {@code code}, which cannot change after creation. */
public record UpdateJobTitleRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String label,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long version) {}
