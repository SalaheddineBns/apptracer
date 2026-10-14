package com.apptracer.team.api;

import com.apptracer.account.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Compte éligible comme membre. {@code currentTeam} : équipe actuelle (RG-05-04), s'il y en a une.
 */
public record TeamMemberCandidateResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) Role role,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
    @Schema(nullable = true) TeamRef currentTeam) {}
