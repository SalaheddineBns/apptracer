package com.apptracer.common.error;

/** Missing resource: 404 without a business rule code. The message is a key (RG-08-06). */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String messageKey) {
    super(messageKey);
  }
}
