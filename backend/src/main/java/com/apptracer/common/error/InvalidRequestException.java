package com.apptracer.common.error;

import java.util.List;

/** Requête invalide hors Bean Validation (par exemple un paramètre de tri inconnu) : 400. */
public class InvalidRequestException extends RuntimeException {

  private final transient List<ApiFieldError> errors;

  public InvalidRequestException(String message, String field, String fieldMessage) {
    super(message);
    this.errors = List.of(new ApiFieldError(field, fieldMessage));
  }

  public List<ApiFieldError> getErrors() {
    return errors;
  }
}
