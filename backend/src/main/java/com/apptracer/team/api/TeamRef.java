package com.apptracer.team.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Référence à une équipe (identifiant et nom). */
public record TeamRef(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {}
