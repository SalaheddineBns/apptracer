package com.apptracer.common.error;

import java.util.List;

/**
 * Violation d'une règle de gestion : traduite en 409, {@code code} = identifiant de la RG. Le
 * message est une clé de {@code messages_*.properties} (RG-08-06), résolue dans la langue de la
 * requête avec {@code args} ; {@code getMessage()} renvoie la clé.
 */
public class BusinessException extends RuntimeException {

  private final String rgCode;
  private final transient Object[] args;
  private final transient List<ApiFieldError> errors;

  public BusinessException(String rgCode, String messageKey, Object... args) {
    this(rgCode, messageKey, List.of(), args);
  }

  private BusinessException(
      String rgCode, String messageKey, List<ApiFieldError> errors, Object... args) {
    super(messageKey);
    this.rgCode = rgCode;
    this.args = args;
    this.errors = List.copyOf(errors);
  }

  /**
   * Variante pour une règle portant sur un seul champ. {@code fieldMessageKey} est la clé du
   * message du champ ; les mêmes {@code args} servent aux deux messages.
   */
  public static BusinessException onField(
      String rgCode, String messageKey, String field, String fieldMessageKey, Object... args) {
    return new BusinessException(
        rgCode, messageKey, List.of(new ApiFieldError(field, fieldMessageKey)), args);
  }

  public String getRgCode() {
    return rgCode;
  }

  /** Erreurs de champ : {@code message} est une clé, résolue par le handler global. */
  public List<ApiFieldError> getErrors() {
    return errors;
  }

  public Object[] getArgs() {
    return args.clone();
  }
}
