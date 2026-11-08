package com.apptracer.referential.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** RG-09-05 : pas de {@code code}, immuable après la création. */
public record UpdateJobTitleRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String label,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long version) {}
