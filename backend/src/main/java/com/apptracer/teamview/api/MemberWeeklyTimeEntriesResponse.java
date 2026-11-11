package com.apptracer.teamview.api;

import com.apptracer.timeentry.api.WeeklyTimeEntriesResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/** A member's journal for one week, same content as My journal (RG-04-05). */
public record MemberWeeklyTimeEntriesResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ManagedTeamResponse team,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TeamViewMember member,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WeeklyTimeEntriesResponse week) {}
