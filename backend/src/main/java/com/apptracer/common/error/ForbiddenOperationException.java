package com.apptracer.common.error;

/**
 * Operation forbidden to the current user: 403, {@code code} = the rule's identifier. The message
 * is a message key (RG-08-06).
 */
public class ForbiddenOperationException extends RuntimeException {

  private final String rgCode;

  public ForbiddenOperationException(String rgCode, String messageKey) {
    super(messageKey);
    this.rgCode = rgCode;
  }

  public String getRgCode() {
    return rgCode;
  }
}
