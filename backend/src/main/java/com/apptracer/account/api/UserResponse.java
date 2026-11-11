package com.apptracer.account.api;

import com.apptracer.account.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/** Account as seen by the ADMIN. Never a password or a hash (CA-03-15). */
public record UserResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) Role role,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
    // RG-09-02: optional job title; RG-09-06: shown even when inactive
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        UserJobTitleResponse jobTitle,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {}
