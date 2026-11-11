package com.apptracer.common.error;

import java.util.List;

/**
 * Invalid request outside Bean Validation (for example an unknown sort parameter): 400. The
 * messages are message keys (RG-08-06), resolved with {@code args}.
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
