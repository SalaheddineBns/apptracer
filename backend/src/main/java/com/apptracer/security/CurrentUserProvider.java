package com.apptracer.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** L'utilisateur courant vient toujours du token (claim {@code sub}), jamais d'un paramètre. */
@Component
public class CurrentUserProvider {

  /** Identifiant de l'utilisateur authentifié. */
  public Long currentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      throw new IllegalStateException("Aucun utilisateur authentifié");
    }
    return Long.valueOf(authentication.getName());
  }
}
