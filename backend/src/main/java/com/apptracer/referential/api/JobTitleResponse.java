package com.apptracer.referential.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Fonction d'un collaborateur (RG-09-10 : le libellé est une donnée, jamais traduit). */
public record JobTitleResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {}
