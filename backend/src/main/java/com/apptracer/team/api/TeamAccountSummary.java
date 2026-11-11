package com.apptracer.team.api;

import com.apptracer.account.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Account as seen in a team (manager or member). RG-05-11: {@code active} flags an inactive
 * account.
 */
public record TeamAccountSummary(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) Role role,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {}
