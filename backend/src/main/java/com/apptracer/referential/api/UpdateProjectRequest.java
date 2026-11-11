package com.apptracer.referential.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * RG-02-01: no {@code code}, which cannot change after creation.
 *
 * @param teamId RG-10-02: when absent, the team is removed and the project no longer has a team
 */
public record UpdateProjectRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
    @Schema(description = "Équipe du projet ; absente, l'équipe est retirée") Long teamId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long version) {}
