package com.apptracer.account.api;

import com.apptracer.account.domain.User;
import com.apptracer.account.service.UserService;
import com.apptracer.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

/** RG-03-11: account management is ADMIN only. */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

  private final UserService userService;
  private final UserMapper mapper;
  private final CurrentUserProvider currentUser;

  public UserController(
      UserService userService, UserMapper mapper, CurrentUserProvider currentUser) {
    this.currentUser = currentUser;
    this.userService = userService;
    this.mapper = mapper;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "listUsers", summary = "Lister les comptes (paginé)")
  @ApiResponse(responseCode = "200", description = "Page de comptes")
  @ApiResponse(
      responseCode = "400",
      description = "Paramètre de pagination ou de tri invalide",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-03-09, RG-03-11)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public PageUserResponse listUsers(
      @ParameterObject
          @PageableDefault(
              size = 20,
              sort = {"lastName", "firstName"})
          Pageable pageable) {
    return mapper.toResponse(userService.list(pageable));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "createUser", summary = "Créer un compte avec un mot de passe initial")
  @ApiResponse(responseCode = "201", description = "Compte créé")
  @ApiResponse(
      responseCode = "400",
      description = "Requête invalide (RG-03-01 format, RG-03-02)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-03-09, RG-03-11)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "E-mail déjà utilisé (RG-03-01), fonction pour un ADMIN (RG-09-02) ou fonction inconnue"
              + " ou inactive (RG-09-06), erreur sur le champ jobTitleCode",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
    User user =
        userService.create(
            request.email(),
            request.firstName(),
            request.lastName(),
            request.role(),
            request.initialPassword(),
            request.jobTitleCode());
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(user.getId());
    return ResponseEntity.created(location).body(mapper.toResponse(user));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "getUser", summary = "Lire un compte")
  @ApiResponse(responseCode = "200", description = "Compte")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-03-09, RG-03-11)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Compte introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public UserResponse getUser(@PathVariable Long id) {
    return mapper.toResponse(userService.get(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      operationId = "updateUser",
      summary = "Modifier le prénom, le nom et le rôle d'un compte")
  @ApiResponse(responseCode = "200", description = "Compte modifié")
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
      description = "Rôle insuffisant (RG-03-09, RG-03-11)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Compte introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Règle RG-03-13 violée (retrait de son propre rôle ADMIN), RG-05-10 violée (rôle MANAGER"
              + " retiré à un compte manager d'une équipe, erreur sur le champ role), RG-09-02 (fonction pour"
              + " un ADMIN) ou RG-09-06 (fonction inconnue ou inactive, non portée par le compte ; erreurs"
              + " sur le champ jobTitleCode), ou compte"
              + " modifié entre-temps (conflit de version)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public UserResponse updateUser(
      @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
    return mapper.toResponse(
        userService.update(
            currentUser.currentUserId(),
            id,
            request.firstName(),
            request.lastName(),
            request.role(),
            request.jobTitleCode(),
            request.version()));
  }

  @PostMapping("/{id}/deactivation")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "deactivateUser", summary = "Désactiver un compte")
  @ApiResponse(
      responseCode = "200",
      description = "Compte désactivé (sans effet s'il l'était déjà)")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-03-09, RG-03-11)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Compte introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Désactivation de son propre compte (RG-03-13), ou d'un compte manager d'une équipe"
              + " (RG-05-10, le message nomme l'équipe)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public UserResponse deactivateUser(@PathVariable Long id) {
    return mapper.toResponse(userService.deactivate(currentUser.currentUserId(), id));
  }

  @PostMapping("/{id}/reactivation")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(operationId = "reactivateUser", summary = "Réactiver un compte")
  @ApiResponse(responseCode = "200", description = "Compte réactivé (sans effet s'il l'était déjà)")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rôle insuffisant (RG-03-09, RG-03-11)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Compte introuvable",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public UserResponse reactivateUser(@PathVariable Long id) {
    return mapper.toResponse(userService.reactivate(id));
  }
}
