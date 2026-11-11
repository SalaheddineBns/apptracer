package com.apptracer.settings.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Application settings. */
public record AppSettingsResponse(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Journée standard en minutes (RG-06-02)")
        int standardDayMinutes,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
    @Schema(
            nullable = true,
            description =
                "Date de la dernière modification (RG-06-05). Null si jamais modifié, ou si"
                    + " l'appelant n'est pas ADMIN")
        Instant lastModifiedAt,
    @Schema(
            nullable = true,
            description =
                "Auteur de la dernière modification (RG-06-05). Null si jamais modifié, ou si"
                    + " l'appelant n'est pas ADMIN")
        SettingsAuthor lastModifiedBy) {}
