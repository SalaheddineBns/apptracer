package com.apptracer.account.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Fonction d'un compte (RG-09-06 : {@code active} faux = mention « Inactive »). */
public record UserJobTitleResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {}
