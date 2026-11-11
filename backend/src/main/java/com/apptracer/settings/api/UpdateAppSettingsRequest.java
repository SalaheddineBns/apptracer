package com.apptracer.settings.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Neither author nor date in the request: they come from the token and the clock (RG-06-05). */
public record UpdateAppSettingsRequest(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Journée standard en minutes, de 60 à 1 440, multiple de 15 (RG-06-02)",
            minimum = "60",
            maximum = "1440",
            multipleOf = 15)
        @NotNull
        @ValidStandardDay
        Integer standardDayMinutes,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long version) {}
