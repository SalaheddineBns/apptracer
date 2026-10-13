package com.apptracer.common.error;

import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduit les exceptions en ProblemDetail. Les 401 / 403 de sécurité sont produits en amont par
 * {@code ProblemDetailAuthenticationEntryPoint} et {@code ProblemDetailAccessDeniedHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  /** Règle de gestion violée : 409 avec le code de la RG. */
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.CONFLICT,
            "business-rule",
            "Règle de gestion violée",
            ex.getMessage(),
            ex.getRgCode(),
            ex.getErrors());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  /** RG-03-04 / RG-03-05 : réponse d'échec unique, quelle que soit la cause. */
  @ExceptionHandler(InvalidCredentialsException.class)
  ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException ex) {
    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.UNAUTHORIZED,
            "invalid-credentials",
            "Identifiants invalides",
            ex.getMessage(),
            "RG-03-04",
            List.of());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
  }

  /** Opération interdite (par exemple saisie d'un autre utilisateur, RG-01-10) : 403 avec code. */
  @ExceptionHandler(ForbiddenOperationException.class)
  ResponseEntity<ProblemDetail> handleForbiddenOperation(ForbiddenOperationException ex) {
    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.FORBIDDEN,
            "forbidden",
            "Accès refusé",
            ex.getMessage(),
            ex.getRgCode(),
            List.of());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.NOT_FOUND,
            "not-found",
            "Ressource introuvable",
            ex.getMessage(),
            null,
            List.of());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
  }

  @ExceptionHandler(InvalidRequestException.class)
  ResponseEntity<ProblemDetail> handleInvalidRequest(InvalidRequestException ex) {
    return ResponseEntity.badRequest()
        .body(
            ProblemDetails.of(
                HttpStatus.BAD_REQUEST,
                "validation",
                "Requête invalide",
                ex.getMessage(),
                null,
                ex.getErrors()));
  }

  /** Conflit de version ({@code @Version}) : 409 sans code de RG. */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> handleOptimisticLocking(
      ObjectOptimisticLockingFailureException ex) {
    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.CONFLICT,
            "concurrent-modification",
            "Modification concurrente",
            "Cet élément a été modifié par quelqu'un d'autre. Rechargez la page puis recommencez.",
            null,
            List.of());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  /**
   * Contraintes SQL connues, traduites comme le contrôle de service équivalent (filet de sécurité
   * en cas de requêtes concurrentes). Toute autre violation est relancée (500).
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
    if (violates(ex, "ux_users_email_lower")) {
      // RG-03-01
      return handleBusiness(
          BusinessException.onField(
              "RG-03-01",
              "Un compte existe déjà avec cet e-mail.",
              "email",
              "Cet e-mail est déjà utilisé."));
    }
    if (violates(ex, "ux_activity_types_code")) {
      // RG-02-01
      return handleBusiness(
          BusinessException.onField(
              "RG-02-01",
              "Un type d'activité existe déjà avec ce code.",
              "code",
              "Ce code est déjà utilisé."));
    }
    if (violates(ex, "ux_projects_code")) {
      // RG-02-01
      return handleBusiness(
          BusinessException.onField(
              "RG-02-01",
              "Un projet existe déjà avec ce code.",
              "code",
              "Ce code est déjà utilisé."));
    }
    throw ex;
  }

  private static boolean violates(Throwable ex, String constraint) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      if (t.getMessage() != null && t.getMessage().contains(constraint)) {
        return true;
      }
    }
    return false;
  }

  /** Bean Validation sur un corps de requête : 400 avec {@code errors[]} par champ. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<ApiFieldError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new ApiFieldError(e.getField(), e.getDefaultMessage()))
            .toList();
    return ResponseEntity.badRequest().body(validationProblem(errors));
  }

  /** Corps illisible (JSON invalide, enum ou date inconnue) : 400. */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return ResponseEntity.badRequest().body(validationProblem(List.of()));
  }

  private static ProblemDetail validationProblem(List<ApiFieldError> errors) {
    return ProblemDetails.of(
        HttpStatus.BAD_REQUEST,
        "validation",
        "Requête invalide",
        "La requête est invalide. Corrigez les champs signalés.",
        null,
        errors);
  }
}
