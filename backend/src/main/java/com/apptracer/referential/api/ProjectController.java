package com.apptracer.referential.api;

import com.apptracer.referential.service.ProjectService;
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
}
