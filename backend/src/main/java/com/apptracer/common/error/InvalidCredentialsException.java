package com.apptracer.common.error;

/**
 * Échec d'authentification (identifiants invalides, compte inactif, refresh token refusé) : 401
 * toujours identique, sans révéler la cause (RG-03-04, RG-03-05).
 */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("Identifiants invalides");
  }
}
