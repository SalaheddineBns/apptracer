package com.apptracer.referential.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateJobTitleRequest(
    // RG-09-05: letters, digits and _, 2 to 30 characters, checked as entered. Lower case passes
    // here so that the case-insensitive uniqueness answers 409 (CA-09-03); the service then refuses
    // a code not written in capitals.
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^[A-Z0-9_]{2,30}$")
        @NotNull
        @Pattern(regexp = "^[A-Za-z0-9_]{2,30}$", message = "{validation.code.pattern}")
        String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String label) {}
