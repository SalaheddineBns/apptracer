package com.apptracer.referential.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * RG-02-01 : pas de {@code code}, immuable après la création.
 *
 * @param teamId RG-10-02 : absente, l'équipe est retirée et le projet devient sans équipe
 */
public record UpdateProjectRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
    @Schema(description = "Équipe du projet ; absente, l'équipe est retirée") Long teamId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long version) {}
