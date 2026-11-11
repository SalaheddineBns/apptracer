package com.apptracer.settings.api;

import com.apptracer.security.CurrentUserProvider;
import com.apptracer.settings.service.AppSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RG-06-03: readable by any signed-in user. */
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

  @PutMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      operationId = "updateAppSettings",
      summary =
          "Modifier la journée standard (ADMIN ; les saisies existantes ne sont pas recalculées)")
  @ApiResponse(responseCode = "200", description = "Paramètres après enregistrement")
  @ApiResponse(
      responseCode = "400",
      description = "Journée standard hors de 60 à 1 440 minutes ou hors pas de 15 (RG-06-02)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-06-03)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Paramètres modifiés entre-temps (conflit de version)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public AppSettingsResponse updateAppSettings(
      @Valid @RequestBody UpdateAppSettingsRequest request) {
    return mapper.toResponse(
        service.update(
            request.standardDayMinutes(), request.version(), currentUser.currentUserId()));
  }
}
