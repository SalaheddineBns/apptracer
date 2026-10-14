package com.apptracer.teamview.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** Semaine d'une équipe : une ligne par membre, triés par nom puis prénom (RG-04-09). */
public record TeamWeekSummaryResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ManagedTeamResponse team,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate weekStart,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate weekEnd,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<MemberWeekSummary> members) {}
