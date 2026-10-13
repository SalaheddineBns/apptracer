package com.apptracer.timeentry.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Projet embarqué dans une saisie, affichable même désactivé (RG-02-05). */
public record ProjectSummary(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {}
