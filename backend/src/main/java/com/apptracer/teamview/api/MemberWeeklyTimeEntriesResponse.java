package com.apptracer.teamview.api;

import com.apptracer.timeentry.api.WeeklyTimeEntriesResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/** Journal d'un membre pour une semaine, même contenu que « Mon journal » (RG-04-05). */
public record MemberWeeklyTimeEntriesResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ManagedTeamResponse team,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TeamViewMember member,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WeeklyTimeEntriesResponse week) {}
