package com.apptracer.auth.api;

import com.apptracer.auth.service.AuthService;
import com.apptracer.auth.service.PasswordChangeService;
import com.apptracer.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

  private final AuthService authService;
  private final CurrentUserProvider currentUser;
  private final AuthMapper mapper;
  private final PasswordChangeService passwordChangeService;

  public AuthController(
      AuthService authService,
      CurrentUserProvider currentUser,
      AuthMapper mapper,
      PasswordChangeService passwordChangeService) {
    this.passwordChangeService = passwordChangeService;
    this.authService = authService;
    this.currentUser = currentUser;
    this.mapper = mapper;
  }

  @PostMapping("/login")
  @Operation(operationId = "login", summary = "Se connecter avec son e-mail et son mot de passe")
  @ApiResponse(responseCode = "200", description = "Connexion réussie")
  @ApiResponse(
      responseCode = "400",
      description = "Requête invalide",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Identifiants invalides (RG-03-04, RG-03-05)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public AuthTokensResponse login(@Valid @RequestBody LoginRequest request) {
    return mapper.toResponse(authService.login(request.email(), request.password()));
  }

  @PostMapping("/refresh")
  @Operation(
      operationId = "refreshSession",
      summary = "Renouveler la session avec un refresh token (usage unique)")
  @ApiResponse(responseCode = "200", description = "Nouveau couple de jetons")
  @ApiResponse(
      responseCode = "400",
      description = "Requête invalide",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Refresh token refusé (RG-03-05, RG-03-07)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public AuthTokensResponse refreshSession(@Valid @RequestBody RefreshSessionRequest request) {
    return mapper.toResponse(authService.refreshSession(request.refreshToken()));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "logout", summary = "Se déconnecter (révoque le refresh token)")
  @ApiResponse(responseCode = "204", description = "Déconnecté")
  @ApiResponse(
      responseCode = "400",
      description = "Requête invalide",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void logout(@Valid @RequestBody LogoutRequest request) {
    authService.logout(request.refreshToken());
  }

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  @Operation(operationId = "getCurrentUser", summary = "Lire le compte connecté")
  @ApiResponse(responseCode = "200", description = "Compte connecté")
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public CurrentUserResponse getCurrentUser() {
    return mapper.toResponse(authService.getCurrentUser(currentUser.currentUserId()));
  }

  @PutMapping("/me/password")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "changeMyPassword",
      summary = "Changer son mot de passe (ancien mot de passe exigé)")
  @ApiResponse(
      responseCode = "204",
      description = "Mot de passe modifié, autres sessions révoquées")
  @ApiResponse(
      responseCode = "400",
      description = "Nouveau mot de passe invalide (RG-03-02)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Access token absent ou invalide (RG-03-09)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Ancien mot de passe incorrect (RG-03-15)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void changeMyPassword(@Valid @RequestBody ChangePasswordRequest request) {
    passwordChangeService.changePassword(
        currentUser.currentUserId(),
        currentUser.currentSessionId().orElse(null),
        request.currentPassword(),
        request.newPassword());
  }
}
