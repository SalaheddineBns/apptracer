package com.apptracer.referential.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
    // RG-02-01 : contrôlé tel que saisi, jamais transformé (CA-02-04)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^[A-Z0-9_]{2,30}$")
        @NotNull
        @Pattern(
            regexp = "^[A-Z0-9_]{2,30}$",
            message = "Le code doit contenir 2 à 30 majuscules, chiffres ou _.")
        String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name) {}
