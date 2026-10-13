package com.apptracer.common.error;

/** Opération interdite à l'utilisateur courant : 403, {@code code} = identifiant de la RG. */
public class ForbiddenOperationException extends RuntimeException {

  private final String rgCode;

  public ForbiddenOperationException(String rgCode, String message) {
    super(message);
    this.rgCode = rgCode;
  }

  public String getRgCode() {
    return rgCode;
  }
}
