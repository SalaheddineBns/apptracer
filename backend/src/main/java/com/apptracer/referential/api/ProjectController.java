package com.apptracer.referential.api;

import com.apptracer.referential.domain.Project;
import com.apptracer.referential.service.ProjectService;
import com.apptracer.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * RG-02-09 : lecture des listes ouverte à tout connecté ; RG-02-08 : écriture réservée à l'ADMIN.
 */
@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects")
public class ProjectController {

  private final ProjectService service;
  private final ReferentialMapper mapper;
  private final CurrentUserProvider currentUser;

  public ProjectController(
      ProjectService service, ReferentialMapper mapper, CurrentUserProvider currentUser) {
    this.service = service;
    this.mapper = mapper;
    this.currentUser = currentUser;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      operationId = "listProjects",
      summary = "Lister les projets (actifs, ou tous pour un ADMIN)")
  @ApiResponse(responseCode = "200", description = "Projets triés par nom")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public List<ProjectResponse> listProjects() {
    return service.list(currentUser.isAdmin()).stream().map(mapper::toResponse).toList();
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "createProject", summary = "Créer un projet")
  @ApiResponse(responseCode = "201", description = "Projet créé")
  @ApiResponse(
      responseCode = "400",
      description = "Requête invalide (RG-02-01 format)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-02-08)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Code déjà utilisé (RG-02-01)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<ProjectResponse> createProject(
      @Valid @RequestBody CreateProjectRequest request) {
    Project project = service.create(request.code(), request.name());
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(project.getId());
    return ResponseEntity.created(location).body(mapper.toResponse(project));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "getProject", summary = "Lire un projet")
  @ApiResponse(responseCode = "200", description = "Projet")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-02-08)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Projet introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ProjectResponse getProject(@PathVariable Long id) {
    return mapper.toResponse(service.get(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "updateProject", summary = "Modifier le nom d'un projet")
  @ApiResponse(responseCode = "200", description = "Projet modifié")
  @ApiResponse(
      responseCode = "400",
      description = "Requête invalide",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-02-08)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Projet introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Projet modifié entre-temps (conflit de version)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ProjectResponse updateProject(
      @PathVariable Long id, @Valid @RequestBody UpdateProjectRequest request) {
    return mapper.toResponse(service.update(id, request.name(), request.version()));
  }

  @PostMapping("/{id}/deactivation")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "deactivateProject", summary = "Désactiver un projet")
  @ApiResponse(
      responseCode = "200",
      description = "Projet désactivé (sans effet s'il l'était déjà)")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-02-08)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Projet introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ProjectResponse deactivateProject(@PathVariable Long id) {
    return mapper.toResponse(service.deactivate(id));
  }

  @PostMapping("/{id}/reactivation")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "reactivateProject", summary = "Réactiver un projet")
  @ApiResponse(responseCode = "200", description = "Projet réactivé (sans effet s'il l'était déjà)")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-02-08)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Projet introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ProjectResponse reactivateProject(@PathVariable Long id) {
    return mapper.toResponse(service.reactivate(id));
  }
}
