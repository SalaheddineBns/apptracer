package com.apptracer.teamview.api;

import com.apptracer.security.CurrentUserProvider;
import com.apptracer.teamview.service.TeamViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RG-04-01, RG-04-03: team view reserved for the MANAGER, read-only. */
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
    // RG-00-03: always the token's account
    return service.listMyManagedTeams(currentUser.currentUserId()).stream()
        .map(mapper::toResponse)
        .toList();
  }

  @GetMapping("/teams/{teamId}/week")
  @PreAuthorize("hasRole('MANAGER')")
  @Operation(
      operationId = "getTeamWeekSummary",
      summary = "Durée par jour et total de la semaine de chaque membre d'une de mes équipes")
  @ApiResponse(responseCode = "200", description = "Membres triés par nom puis prénom")
  @ApiResponse(
      responseCode = "400",
      description = "weekStart absent, mal formé ou qui n'est pas un lundi",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-04-01) ou équipe hors de mon périmètre (RG-04-02)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public TeamWeekSummaryResponse getTeamWeekSummary(
      @PathVariable Long teamId,
      @RequestParam @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Un lundi")
          LocalDate weekStart) {
    return mapper.toResponse(
        service.getTeamWeekSummary(currentUser.currentUserId(), teamId, weekStart));
  }

  @GetMapping("/members/{userId}/time-entries")
  @PreAuthorize("hasRole('MANAGER')")
  @Operation(
      operationId = "listTeamMemberTimeEntries",
      summary = "Journal de la semaine d'un membre de mes équipes (lecture seule)")
  @ApiResponse(responseCode = "200", description = "Saisies de la semaine et total en minutes")
  @ApiResponse(
      responseCode = "400",
      description = "weekStart absent, mal formé ou qui n'est pas un lundi",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-04-01) ou compte hors de mes équipes (RG-04-02)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public MemberWeeklyTimeEntriesResponse listTeamMemberTimeEntries(
      @PathVariable Long userId,
      @RequestParam @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Un lundi")
          LocalDate weekStart) {
    // RG-00-03: the manager comes from the token
    return mapper.toResponse(
        service.listTeamMemberTimeEntries(currentUser.currentUserId(), userId, weekStart));
  }
}
