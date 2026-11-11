package com.apptracer.account.api;

import com.apptracer.account.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * RG-03-11: first name, last name, role and job title only; the e-mail and the password cannot be
 * changed.
 */
public record UpdateUserRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100)
        String firstName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String lastName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) @NotNull Role role,
    // RG-09-04: optional job title (code); when absent, the account has no job title
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String jobTitleCode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long version) {}
