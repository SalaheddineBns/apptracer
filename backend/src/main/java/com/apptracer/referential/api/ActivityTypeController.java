package com.apptracer.referential.api;

import com.apptracer.referential.service.ActivityTypeService;
import com.apptracer.security.CurrentUserProvider;
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

/** RG-02-09 : lecture ouverte à tout utilisateur connecté. */
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
}
