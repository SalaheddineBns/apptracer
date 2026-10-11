package com.apptracer.account.api;

import com.apptracer.account.domain.User;
import com.apptracer.account.service.UserService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** RG-03-11 : gestion des comptes réservée à l'ADMIN. */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

  private final UserService userService;
  private final UserMapper mapper;

  public UserController(UserService userService, UserMapper mapper) {
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
      description = "E-mail déjà utilisé (RG-03-01)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
    User user =
        userService.create(
            request.email(),
            request.firstName(),
            request.lastName(),
            request.role(),
            request.initialPassword());
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
}
