package com.apptracer.common.error;

import java.util.List;

/**
 * Violation of a business rule: translated into a 409, {@code code} = the rule's identifier. The
 * message is a key of {@code messages_*.properties} (RG-08-06), resolved in the request's language
 * with {@code args}; {@code getMessage()} returns the key.
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
   * Variant for a rule on a single field. {@code fieldMessageKey} is the key of the field's
   * message; the same {@code args} are used for both messages.
   */
  public static BusinessException onField(
      String rgCode, String messageKey, String field, String fieldMessageKey, Object... args) {
    return new BusinessException(
        rgCode, messageKey, List.of(new ApiFieldError(field, fieldMessageKey)), args);
  }

  public String getRgCode() {
    return rgCode;
  }

  /** Field errors: {@code message} is a key, resolved by the global handler. */
  public List<ApiFieldError> getErrors() {
    return errors;
  }

  public Object[] getArgs() {
    return args.clone();
  }
}
