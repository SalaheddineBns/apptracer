package com.apptracer.common.error;

/** Ressource inexistante : 404 sans code de règle de gestion. Le message est une clé (RG-08-06). */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String messageKey) {
    super(messageKey);
  }
}
