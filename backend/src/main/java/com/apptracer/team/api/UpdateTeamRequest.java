package com.apptracer.team.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Replaces the name, the manager and the full list of members in one go. */
public record UpdateTeamRequest(
    // RG-05-01: 2 to 100 characters, surrounding spaces removed by the service
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 2, maxLength = 100)
        @NotNull
        @Size(min = 2, max = 100, message = "{validation.team.name.size}")
        String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long managerId,
    // RG-05-07: may be empty
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull List<@NotNull Long> memberIds,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long version) {}
