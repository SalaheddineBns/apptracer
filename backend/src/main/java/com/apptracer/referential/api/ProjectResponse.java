package com.apptracer.referential.api;

import com.apptracer.team.api.TeamRef;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Project.
 *
 * @param team RG-10-01: the project's team, absent for a project without a team
 */
public record ProjectResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
    @Schema(description = "Équipe du projet, absente pour un projet commun") TeamRef team,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {}
