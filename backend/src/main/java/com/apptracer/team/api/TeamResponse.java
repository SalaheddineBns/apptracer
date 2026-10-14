package com.apptracer.team.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Équipe avec son manager et ses membres triés par nom puis prénom (RG-05-13). */
public record TeamResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TeamAccountSummary manager,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TeamAccountSummary> members,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int memberCount,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {}
