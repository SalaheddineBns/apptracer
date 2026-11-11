package com.apptracer.team.api;

import com.apptracer.security.CurrentUserProvider;
import com.apptracer.team.service.TeamService;
import com.apptracer.team.service.TeamService.TeamDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** RG-05-08: all team management is ADMIN only. */
@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams")
public class TeamController {

  private final TeamService service;
  private final TeamMapper mapper;
  private final CurrentUserProvider currentUser;

  public TeamController(TeamService service, TeamMapper mapper, CurrentUserProvider currentUser) {
    this.service = service;
    this.mapper = mapper;
    this.currentUser = currentUser;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      operationId = "listTeams",
      summary = "Lister les équipes avec leurs membres (triées par nom)")
  @ApiResponse(responseCode = "200", description = "Équipes triées par nom, membres par nom/prénom")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-05-08)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public List<TeamResponse> listTeams() {
    return service.list().stream().map(mapper::toResponse).toList();
  }

  @GetMapping("/manager-candidates")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      operationId = "listTeamManagerCandidates",
      summary = "Lister les comptes pouvant être manager (actifs, rôle MANAGER)")
  @ApiResponse(responseCode = "200", description = "Comptes triés par nom puis prénom")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-05-08)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public List<TeamAccountSummary> listTeamManagerCandidates() {
    return service.managerCandidates().stream().map(mapper::toSummary).toList();
  }

  @GetMapping("/member-candidates")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      operationId = "listTeamMemberCandidates",
      summary = "Lister les comptes pouvant être membre, avec leur équipe actuelle")
  @ApiResponse(
      responseCode = "200",
      description = "Comptes USER ou MANAGER (actifs ou non) triés par nom puis prénom")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-05-08)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public List<TeamMemberCandidateResponse> listTeamMemberCandidates() {
    return service.memberCandidates().stream().map(mapper::toCandidate).toList();
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "createTeam", summary = "Créer une équipe")
  @ApiResponse(responseCode = "201", description = "Équipe créée")
  @ApiResponse(
      responseCode = "400",
      description = "Requête invalide (RG-05-01 longueur du nom)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-05-08)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Règle de gestion violée : RG-05-01 (nom déjà utilisé), RG-05-02 (manager non éligible),"
              + " RG-05-03 (membre non éligible), RG-05-04 (membre déjà dans une autre équipe),"
              + " RG-05-05 (manager choisi comme membre)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<TeamResponse> createTeam(@Valid @RequestBody CreateTeamRequest request) {
    TeamDetails created =
        service.create(
            currentUser.currentUserId(), request.name(), request.managerId(), request.memberIds());
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .build(created.team().getId());
    return ResponseEntity.created(location).body(mapper.toResponse(created));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "getTeam", summary = "Lire une équipe")
  @ApiResponse(responseCode = "200", description = "Équipe, membres triés par nom puis prénom")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-05-08)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Équipe introuvable (ou supprimée)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public TeamResponse getTeam(@PathVariable Long id) {
    return mapper.toResponse(service.get(id));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      operationId = "deleteTeam",
      summary = "Supprimer définitivement une équipe (comptes et saisies des membres intacts)")
  @ApiResponse(responseCode = "204", description = "Équipe supprimée")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-05-08)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Équipe introuvable (ou déjà supprimée)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void deleteTeam(@PathVariable Long id) {
    service.delete(currentUser.currentUserId(), id);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      operationId = "updateTeam",
      summary = "Modifier une équipe (nom, manager et liste complète des membres)")
  @ApiResponse(responseCode = "200", description = "Équipe modifiée")
  @ApiResponse(
      responseCode = "400",
      description = "Requête invalide (RG-05-01 longueur du nom)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-05-08)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Équipe introuvable (ou supprimée)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Règle de gestion violée : RG-05-01 (nom déjà utilisé), RG-05-02 (manager non éligible),"
              + " RG-05-03 (membre non éligible), RG-05-04 (membre déjà dans une autre équipe),"
              + " RG-05-05 (manager choisi comme membre) ; ou équipe modifiée entre-temps"
              + " (conflit de version, sans code)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public TeamResponse updateTeam(
      @PathVariable Long id, @Valid @RequestBody UpdateTeamRequest request) {
    return mapper.toResponse(
        service.update(
            currentUser.currentUserId(),
            id,
            request.name(),
            request.managerId(),
            request.memberIds(),
            request.version()));
  }
}
