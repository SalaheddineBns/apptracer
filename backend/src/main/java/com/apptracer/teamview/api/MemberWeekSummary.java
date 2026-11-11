package com.apptracer.teamview.api;

import com.apptracer.common.api.DailyTotal;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** A member and their seven days, Monday to Sunday (RG-04-05). */
public record MemberWeekSummary(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TeamViewMember member,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DailyTotal> days,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalMinutes) {}
