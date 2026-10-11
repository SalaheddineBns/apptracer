package com.apptracer.common.error;

/** Ressource inexistante : 404 sans code de règle de gestion. */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }
}
