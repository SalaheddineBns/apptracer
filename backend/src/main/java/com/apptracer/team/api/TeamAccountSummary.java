package com.apptracer.team.api;

import com.apptracer.account.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/** Compte vu dans une équipe (manager ou membre). RG-05-11 : {@code active} signale l'inactif. */
public record TeamAccountSummary(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) Role role,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {}
