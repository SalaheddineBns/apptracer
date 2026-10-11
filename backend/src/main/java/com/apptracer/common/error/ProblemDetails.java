package com.apptracer.common.error;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Fabrique des réponses d'erreur au format ProblemDetail décrit dans le skill conventions-api. */
public final class ProblemDetails {

  private static final String BASE = "https://apptracer/errors/";

  private ProblemDetails() {}

  public static ProblemDetail of(
      HttpStatus status,
      String typeSlug,
      String title,
      String detail,
      String code,
      List<ApiFieldError> errors) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(BASE + typeSlug));
    problem.setTitle(title);
    if (code != null) {
      problem.setProperty("code", code);
    }
    if (errors != null && !errors.isEmpty()) {
      problem.setProperty("errors", errors);
    }
    return problem;
  }
}
