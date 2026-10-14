package com.apptracer.teamview.api;

import com.apptracer.security.CurrentUserProvider;
import com.apptracer.teamview.service.TeamViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RG-04-01, RG-04-03 : vue équipe réservée au MANAGER, uniquement des lectures. */
@RestController
@RequestMapping("/api/v1/team-view")
@Tag(name = "TeamView")
public class TeamViewController {

  private final TeamViewService service;
  private final TeamViewMapper mapper;
  private final CurrentUserProvider currentUser;

  public TeamViewController(
      TeamViewService service, TeamViewMapper mapper, CurrentUserProvider currentUser) {
    this.service = service;
    this.mapper = mapper;
    this.currentUser = currentUser;
  }

  @GetMapping("/teams")
  @PreAuthorize("hasRole('MANAGER')")
  @Operation(
      operationId = "listMyManagedTeams",
      summary = "Lister les équipes dont je suis manager (triées par nom)")
  @ApiResponse(responseCode = "200", description = "Équipes, liste vide si aucune (RG-04-01)")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-04-01)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public List<ManagedTeamResponse> listMyManagedTeams() {
    // RG-00-03 : toujours le compte du jeton
    return service.listMyManagedTeams(currentUser.currentUserId()).stream()
        .map(mapper::toResponse)
        .toList();
  }
}
