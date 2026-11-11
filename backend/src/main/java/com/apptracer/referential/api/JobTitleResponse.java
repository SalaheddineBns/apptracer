package com.apptracer.referential.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Job title of an employee (RG-09-10: the label is data, never translated). */
public record JobTitleResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {}
