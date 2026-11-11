package com.apptracer.referential.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * RG-10-11: "My projects".
 *
 * @param teams teams the account belongs to or manages, sorted by name; empty for an account
 *     without a team
 * @param sharedProjects active projects without a team, sorted by name
 */
public record MyProjectsResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TeamProjectsResponse> teams,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ProjectResponse> sharedProjects) {}
