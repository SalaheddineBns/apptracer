package com.apptracer.auth.api;

import com.apptracer.account.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/** Signed-in account. Never contains the password or its hash (CA-03-15). */
public record CurrentUserResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) Role role) {}
