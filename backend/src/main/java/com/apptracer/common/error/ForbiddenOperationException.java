package com.apptracer.common.error;

/**
 * Opération interdite à l'utilisateur courant : 403, {@code code} = identifiant de la RG. Le
 * message est une clé de message (RG-08-06).
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
