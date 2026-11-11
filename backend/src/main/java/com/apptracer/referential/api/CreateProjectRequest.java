package com.apptracer.referential.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creation of a project.
 *
 * @param teamId RG-10-01, RG-10-02: optional team; when absent, the project has no team
 */
public record CreateProjectRequest(
    // RG-02-01: checked as entered, never transformed (CA-02-04)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^[A-Z0-9_]{2,30}$")
        @NotNull
        @Pattern(regexp = "^[A-Z0-9_]{2,30}$", message = "{validation.code.pattern}")
        String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
    @Schema(description = "Équipe du projet ; absente pour un projet commun") Long teamId) {}
