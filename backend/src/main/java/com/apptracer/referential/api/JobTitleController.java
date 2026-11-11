package com.apptracer.referential.api;

import com.apptracer.referential.domain.JobTitle;
import com.apptracer.referential.service.JobTitleService;
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

/** RG-09-07: active ones are readable by anyone signed in; writing is ADMIN only. */
@RestController
@RequestMapping("/api/v1/job-titles")
@Tag(name = "JobTitles")
public class JobTitleController {

  private final JobTitleService service;
  private final ReferentialMapper mapper;
  private final CurrentUserProvider currentUser;

  public JobTitleController(
      JobTitleService service, ReferentialMapper mapper, CurrentUserProvider currentUser) {
    this.service = service;
    this.mapper = mapper;
    this.currentUser = currentUser;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      operationId = "listJobTitles",
      summary = "Lister les fonctions (actives, ou toutes pour un ADMIN)")
  @ApiResponse(responseCode = "200", description = "Fonctions triées par libellé (RG-09-08)")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public List<JobTitleResponse> listJobTitles() {
    return service.list(currentUser.isAdmin()).stream().map(mapper::toResponse).toList();
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "createJobTitle", summary = "Créer une fonction")
  @ApiResponse(responseCode = "201", description = "Fonction créée")
  @ApiResponse(
      responseCode = "400",
      description = "Requête invalide (RG-09-05 : format du code, libellé)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-09-07)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Code déjà utilisé (RG-09-05)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<JobTitleResponse> createJobTitle(
      @Valid @RequestBody CreateJobTitleRequest request) {
    JobTitle title = service.create(request.code(), request.label());
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(title.getId());
    return ResponseEntity.created(location).body(mapper.toResponse(title));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "getJobTitle", summary = "Lire une fonction")
  @ApiResponse(responseCode = "200", description = "Fonction")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-09-07)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Fonction introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public JobTitleResponse getJobTitle(@PathVariable Long id) {
    return mapper.toResponse(service.get(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      operationId = "updateJobTitle",
      summary = "Modifier le libellé d'une fonction (le code est immuable)")
  @ApiResponse(responseCode = "200", description = "Fonction modifiée")
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
      description = "Rôle insuffisant (RG-09-07)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Fonction introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Fonction modifiée entre-temps (conflit de version)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public JobTitleResponse updateJobTitle(
      @PathVariable Long id, @Valid @RequestBody UpdateJobTitleRequest request) {
    return mapper.toResponse(service.update(id, request.label(), request.version()));
  }

  @PostMapping("/{id}/deactivation")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "deactivateJobTitle", summary = "Désactiver une fonction")
  @ApiResponse(
      responseCode = "200",
      description = "Fonction désactivée (sans effet si elle l'était déjà)")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-09-07)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Fonction introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public JobTitleResponse deactivateJobTitle(@PathVariable Long id) {
    return mapper.toResponse(service.deactivate(id));
  }

  @PostMapping("/{id}/reactivation")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "reactivateJobTitle", summary = "Réactiver une fonction")
  @ApiResponse(
      responseCode = "200",
      description = "Fonction réactivée (sans effet si elle l'était déjà)")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-09-07)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Fonction introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public JobTitleResponse reactivateJobTitle(@PathVariable Long id) {
    return mapper.toResponse(service.reactivate(id));
  }
}
