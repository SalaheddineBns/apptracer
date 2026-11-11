package com.apptracer.referential.api;

import com.apptracer.team.api.TeamRef;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Projet.
 *
 * @param team RG-10-01 : équipe du projet, absente pour un projet sans équipe
 */
public record ProjectResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
    @Schema(description = "Équipe du projet, absente pour un projet commun") TeamRef team,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {}
