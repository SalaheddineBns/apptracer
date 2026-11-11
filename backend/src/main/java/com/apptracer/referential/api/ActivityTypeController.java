package com.apptracer.referential.api;

import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.service.ActivityTypeService;
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

/** RG-02-09: lists are readable by anyone signed in; RG-02-08: writing is ADMIN only. */
@RestController
@RequestMapping("/api/v1/activity-types")
@Tag(name = "ActivityTypes")
public class ActivityTypeController {

  private final ActivityTypeService service;
  private final ReferentialMapper mapper;
  private final CurrentUserProvider currentUser;

  public ActivityTypeController(
      ActivityTypeService service, ReferentialMapper mapper, CurrentUserProvider currentUser) {
    this.service = service;
    this.mapper = mapper;
    this.currentUser = currentUser;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      operationId = "listActivityTypes",
      summary = "Lister les types d'activité (actifs, ou tous pour un ADMIN)")
  @ApiResponse(responseCode = "200", description = "Types triés par libellé")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public List<ActivityTypeResponse> listActivityTypes() {
    return service.list(currentUser.isAdmin()).stream().map(mapper::toResponse).toList();
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "createActivityType", summary = "Créer un type d'activité")
  @ApiResponse(responseCode = "201", description = "Type créé")
  @ApiResponse(
      responseCode = "400",
      description = "Requête invalide (RG-02-01 format, RG-02-03, RG-02-04)",
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
  public ResponseEntity<ActivityTypeResponse> createActivityType(
      @Valid @RequestBody CreateActivityTypeRequest request) {
    ActivityType type =
        service.create(request.code(), request.label(), request.projectRequired(), request.color());
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(type.getId());
    return ResponseEntity.created(location).body(mapper.toResponse(type));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "getActivityType", summary = "Lire un type d'activité")
  @ApiResponse(responseCode = "200", description = "Type d'activité")
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
      description = "Type introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ActivityTypeResponse getActivityType(@PathVariable Long id) {
    return mapper.toResponse(service.get(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      operationId = "updateActivityType",
      summary = "Modifier le libellé, la couleur et « projet requis » d'un type")
  @ApiResponse(responseCode = "200", description = "Type modifié")
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
      description = "Type introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Règle RG-02-07 violée, ou type modifié entre-temps (conflit de version)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ActivityTypeResponse updateActivityType(
      @PathVariable Long id, @Valid @RequestBody UpdateActivityTypeRequest request) {
    return mapper.toResponse(
        service.update(
            id, request.label(), request.projectRequired(), request.color(), request.version()));
  }

  @PostMapping("/{id}/deactivation")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "deactivateActivityType", summary = "Désactiver un type d'activité")
  @ApiResponse(responseCode = "200", description = "Type désactivé (sans effet s'il l'était déjà)")
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
      description = "Type introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ActivityTypeResponse deactivateActivityType(@PathVariable Long id) {
    return mapper.toResponse(service.deactivate(id));
  }

  @PostMapping("/{id}/reactivation")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "reactivateActivityType", summary = "Réactiver un type d'activité")
  @ApiResponse(responseCode = "200", description = "Type réactivé (sans effet s'il l'était déjà)")
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
      description = "Type introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ActivityTypeResponse reactivateActivityType(@PathVariable Long id) {
    return mapper.toResponse(service.reactivate(id));
  }
}
