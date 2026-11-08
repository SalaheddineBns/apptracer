package com.apptracer.common.error;

import java.util.List;

/**
 * Requête invalide hors Bean Validation (par exemple un paramètre de tri inconnu) : 400. Les
 * messages sont des clés de message (RG-08-06), résolues avec {@code args}.
 */
public class InvalidRequestException extends RuntimeException {

  private final transient Object[] args;
  private final transient List<ApiFieldError> errors;

  public InvalidRequestException(
      String messageKey, String field, String fieldMessageKey, Object... args) {
    super(messageKey);
    this.args = args;
    this.errors = List.of(new ApiFieldError(field, fieldMessageKey));
  }

  public List<ApiFieldError> getErrors() {
    return errors;
  }

  public Object[] getArgs() {
    return args.clone();
  }
}
