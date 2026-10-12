package com.apptracer.referential.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** RG-02-01 : pas de {@code code}, immuable après la création. */
public record UpdateProjectRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long version) {}
