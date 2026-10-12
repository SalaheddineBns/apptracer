package com.apptracer.settings.api;

import com.apptracer.security.CurrentUserProvider;
import com.apptracer.settings.service.AppSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RG-06-03 : lecture ouverte à tout utilisateur connecté. */
@RestController
@RequestMapping("/api/v1/app-settings")
@Tag(name = "AppSettings")
public class AppSettingsController {

  private final AppSettingsService service;
  private final AppSettingsMapper mapper;
  private final CurrentUserProvider currentUser;

  public AppSettingsController(
      AppSettingsService service, AppSettingsMapper mapper, CurrentUserProvider currentUser) {
    this.service = service;
    this.mapper = mapper;
    this.currentUser = currentUser;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      operationId = "getAppSettings",
      summary =
          "Lire les paramètres (journée standard ; dernière modification renvoyée à l'ADMIN seulement)")
  @ApiResponse(responseCode = "200", description = "Paramètres de l'application")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public AppSettingsResponse getAppSettings() {
    return mapper.toResponse(service.read(currentUser.isAdmin()));
  }
}
