package com.apptracer.common.error;

import java.util.List;
import java.util.Locale;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions into ProblemDetail responses. Security 401 / 403 responses are produced
 * upstream by {@code ProblemDetailAuthenticationEntryPoint} and {@code
 * ProblemDetailAccessDeniedHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private final MessageSource messages;

  public GlobalExceptionHandler(MessageSource messages) {
    this.messages = messages;
  }

  /** RG-08-06: message resolved in the request's language (Accept-Language). */
  private String msg(String key, Object... args) {
    Locale locale = LocaleContextHolder.getLocale();
    return messages.getMessage(key, args, locale);
  }

  private List<ApiFieldError> localized(List<ApiFieldError> errors, Object[] args) {
    return errors.stream().map(e -> new ApiFieldError(e.field(), msg(e.message(), args))).toList();
  }

  /** Business rule violated: 409 with the rule's code. */
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.CONFLICT,
            "business-rule",
            msg("error.title.business-rule"),
            msg(ex.getMessage(), ex.getArgs()),
            ex.getRgCode(),
            localized(ex.getErrors(), ex.getArgs()));
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  /** RG-03-04 / RG-03-05: a single failure response, whatever the cause. */
  @ExceptionHandler(InvalidCredentialsException.class)
  ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException ex) {
    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.UNAUTHORIZED,
            "invalid-credentials",
            msg("error.title.invalid-credentials"),
            msg(ex.getMessage()),
            "RG-03-04",
            List.of());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
  }

  /** Forbidden operation (for example another user's time entry, RG-01-10): 403 with a code. */
  @ExceptionHandler(ForbiddenOperationException.class)
  ResponseEntity<ProblemDetail> handleForbiddenOperation(ForbiddenOperationException ex) {
    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.FORBIDDEN,
            "forbidden",
            msg("error.title.forbidden"),
            msg(ex.getMessage()),
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
            msg("error.title.not-found"),
            msg(ex.getMessage()),
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
                msg("error.title.validation"),
                msg(ex.getMessage(), ex.getArgs()),
                null,
                localized(ex.getErrors(), ex.getArgs())));
  }

  /** Version conflict ({@code @Version}): 409 without a rule code. */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> handleOptimisticLocking(
      ObjectOptimisticLockingFailureException ex) {
    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.CONFLICT,
            "concurrent-modification",
            msg("error.title.concurrent-modification"),
            msg("error.concurrent-modification.detail"),
            null,
            List.of());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  /**
   * Known SQL constraints, translated like the equivalent service check (safety net for concurrent
   * requests). Any other violation is rethrown (500).
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
    if (violates(ex, "ux_users_email_lower")) {
      // RG-03-01
      return handleBusiness(
          BusinessException.onField(
              "RG-03-01",
              "error.account.email-duplicate",
              "email",
              "field.account.email-duplicate"));
    }
    if (violates(ex, "ux_activity_types_code")) {
      // RG-02-01
      return handleBusiness(
          BusinessException.onField(
              "RG-02-01", "error.activity-type.code-duplicate", "code", "field.code-duplicate"));
    }
    if (violates(ex, "ux_projects_code")) {
      // RG-02-01
      return handleBusiness(
          BusinessException.onField(
              "RG-02-01", "error.project.code-duplicate", "code", "field.code-duplicate"));
    }
    if (violates(ex, "ux_job_titles_code_lower")) {
      // RG-09-05
      return handleBusiness(
          BusinessException.onField(
              "RG-09-05", "error.job-title.code-duplicate", "code", "field.code-duplicate"));
    }
    if (violates(ex, "ex_time_entries_no_overlap")) {
      // RG-01-06 : deux requêtes simultanées ont passé le contrôle de service
      return handleBusiness(new BusinessException("RG-01-06", "error.time-entry.overlap.generic"));
    }
    if (violates(ex, "ux_teams_name_lower")) {
      // RG-05-01
      return handleBusiness(
          BusinessException.onField(
              "RG-05-01", "error.team.name-duplicate", "name", "field.team.name-duplicate"));
    }
    if (violates(ex, "pk_team_members")) {
      // RG-05-04 : deux requêtes simultanées ont rattaché le même compte
      return handleBusiness(
          BusinessException.onField(
              "RG-05-04",
              "error.team.member-concurrent",
              "memberIds",
              "field.team.member-concurrent"));
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

  /** Bean Validation on a request body: 400 with {@code errors[]} per field. */
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

  /** Missing required request parameter: 400 with the parameter's name in error. */
  @Override
  protected ResponseEntity<Object> handleMissingServletRequestParameter(
      MissingServletRequestParameterException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return ResponseEntity.badRequest()
        .body(
            validationProblem(
                List.of(new ApiFieldError(ex.getParameterName(), msg("field.param-required")))));
  }

  /** Malformed request parameter (for example an unreadable date): 400 on that parameter. */
  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String field = ex.getPropertyName() == null ? "request" : ex.getPropertyName();
    return ResponseEntity.badRequest()
        .body(validationProblem(List.of(new ApiFieldError(field, msg("field.invalid-value")))));
  }

  private ProblemDetail validationProblem(List<ApiFieldError> errors) {
    return ProblemDetails.of(
        HttpStatus.BAD_REQUEST,
        "validation",
        msg("error.title.validation"),
        msg("error.validation.detail"),
        null,
        errors);
  }
}
