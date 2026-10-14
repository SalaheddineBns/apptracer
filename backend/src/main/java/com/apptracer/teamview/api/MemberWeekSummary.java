package com.apptracer.teamview.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Un membre et ses sept jours, lundi à dimanche (RG-04-05). */
public record MemberWeekSummary(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TeamViewMember member,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DailyTotal> days,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalMinutes) {}
