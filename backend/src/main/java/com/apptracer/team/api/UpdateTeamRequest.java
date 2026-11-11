package com.apptracer.team.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Replaces the name, the manager and the full list of members in one go. */
public record UpdateTeamRequest(
    // RG-05-01 : 2 à 100 caractères, espaces de bord retirés par le service
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 2, maxLength = 100)
        @NotNull
        @Size(min = 2, max = 100, message = "{validation.team.name.size}")
        String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long managerId,
    // RG-05-07 : peut être vide
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull List<@NotNull Long> memberIds,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long version) {}
