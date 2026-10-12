package com.apptracer.settings.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Auteur de la dernière modification (RG-06-05). */
public record SettingsAuthor(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastName) {}
