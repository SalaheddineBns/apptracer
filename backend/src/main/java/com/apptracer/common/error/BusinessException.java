package com.apptracer.common.error;

import java.util.List;

/** Violation d'une règle de gestion : traduite en 409, {@code code} = identifiant de la RG. */
public class BusinessException extends RuntimeException {

  private final String rgCode;
  private final transient List<ApiFieldError> errors;

  public BusinessException(String rgCode, String message) {
    this(rgCode, message, List.of());
  }

  public BusinessException(String rgCode, String message, List<ApiFieldError> errors) {
    super(message);
    this.rgCode = rgCode;
    this.errors = List.copyOf(errors);
  }

  /** Variante pour une règle portant sur un seul champ. */
  public static BusinessException onField(
      String rgCode, String message, String field, String fieldMessage) {
    return new BusinessException(rgCode, message, List.of(new ApiFieldError(field, fieldMessage)));
  }

  public String getRgCode() {
    return rgCode;
  }

  public List<ApiFieldError> getErrors() {
    return errors;
  }
}
