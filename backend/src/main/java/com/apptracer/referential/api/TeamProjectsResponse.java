package com.apptracer.referential.api;

import com.apptracer.team.api.TeamRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** RG-10-11: one of the account's teams and its active projects, sorted by name. */
public record TeamProjectsResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TeamRef team,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ProjectResponse> projects) {}
