package com.apptracer.common.error;

/**
 * Authentication failure (invalid credentials, inactive account, refused refresh token): always the
 * same 401, without revealing the cause (RG-03-04, RG-03-05).
 */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("error.invalid-credentials.detail");
  }
}
