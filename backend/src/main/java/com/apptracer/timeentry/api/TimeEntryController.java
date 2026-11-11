package com.apptracer.timeentry.api;

import com.apptracer.security.CurrentUserProvider;
import com.apptracer.timeentry.domain.TimeEntry;
import com.apptracer.timeentry.service.TimeEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** RG-01-14: time entries are open to the USER and MANAGER roles (an ADMIN has no journal). */
@RestController
@RequestMapping("/api/v1/time-entries")
@Tag(name = "TimeEntries")
public class TimeEntryController {

  private final TimeEntryService service;
  private final TimeEntryMapper mapper;
  private final CurrentUserProvider currentUser;

  public TimeEntryController(
      TimeEntryService service, TimeEntryMapper mapper, CurrentUserProvider currentUser) {
    this.service = service;
    this.mapper = mapper;
    this.currentUser = currentUser;
  }

  @GetMapping
  @PreAuthorize("hasRole('USER')")
  @Operation(
      operationId = "listMyTimeEntries",
      summary = "Lister mes saisies d'une semaine (lundi à dimanche) avec le total")
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
      description = "Rôle insuffisant (RG-01-14)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public WeeklyTimeEntriesResponse listMyTimeEntries(
      @RequestParam @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Un lundi")
          LocalDate weekStart) {
    // RG-00-03 : toujours le compte du jeton
    return mapper.toResponse(service.listWeek(currentUser.currentUserId(), weekStart));
  }

  @PostMapping
  @PreAuthorize("hasRole('USER')")
  @Operation(operationId = "createTimeEntry", summary = "Déclarer une activité")
  @ApiResponse(responseCode = "201", description = "Saisie créée, durée calculée et figée")
  @ApiResponse(
      responseCode = "400",
      description =
          "Champs requis ou interdits selon la granularité, format, commentaire (RG-01-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-01-14)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Règle violée : RG-01-01, RG-01-03, RG-01-04, RG-01-05, RG-01-07, RG-02-06",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<TimeEntryResponse> createTimeEntry(
      @Valid @RequestBody CreateTimeEntryRequest request) {
    // RG-00-03 : le propriétaire vient du jeton
    TimeEntry entry = service.create(currentUser.currentUserId(), mapper.toCommand(request));
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(entry.getId());
    return ResponseEntity.created(location).body(mapper.toResponse(entry));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('USER')")
  @Operation(operationId = "getMyTimeEntry", summary = "Lire une de ses saisies")
  @ApiResponse(responseCode = "200", description = "Saisie")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-01-14) ou saisie d'un autre utilisateur (RG-01-10)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Saisie introuvable ou supprimée (RG-01-11)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public TimeEntryResponse getMyTimeEntry(@PathVariable Long id) {
    return mapper.toResponse(service.getOwn(currentUser.currentUserId(), id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('USER')")
  @Operation(
      operationId = "updateTimeEntry",
      summary = "Modifier une de ses saisies (tous les champs, granularité comprise)")
  @ApiResponse(responseCode = "200", description = "Saisie modifiée, durée recalculée et refigée")
  @ApiResponse(
      responseCode = "400",
      description = "Champs requis ou interdits selon la granularité, format, commentaire",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-01-14) ou saisie d'un autre utilisateur (RG-01-10)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Saisie introuvable ou supprimée (RG-01-11)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Règle violée (RG-01-01, RG-01-03, RG-01-04, RG-01-05, RG-01-06, RG-01-07, RG-02-06)"
              + " ou saisie modifiée entre-temps (conflit de version, sans code)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public TimeEntryResponse updateTimeEntry(
      @PathVariable Long id, @Valid @RequestBody UpdateTimeEntryRequest request) {
    return mapper.toResponse(
        service.update(
            currentUser.currentUserId(), id, request.version(), mapper.toCommand(request)));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('USER')")
  @Operation(
      operationId = "deleteTimeEntry",
      summary = "Supprimer une de ses saisies (suppression logique)")
  @ApiResponse(responseCode = "204", description = "Saisie supprimée")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-01-14) ou saisie d'un autre utilisateur (RG-01-10)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Saisie introuvable ou déjà supprimée (RG-01-11)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<Void> deleteTimeEntry(@PathVariable Long id) {
    service.delete(currentUser.currentUserId(), id);
    return ResponseEntity.noContent().build();
  }
}
