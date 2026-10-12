package com.apptracer.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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

  /** Vrai si l'utilisateur authentifié a le rôle ADMIN (authority {@code ROLE_ADMIN}). */
  public boolean isAdmin() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
  }

  /** Identifiant de la session courante (claim {@code sid} de l'access token), s'il est présent. */
  public Optional<UUID> currentSessionId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwt) {
      String sid = jwt.getToken().getClaimAsString(AccessTokenService.CLAIM_SESSION_ID);
      if (sid != null) {
        try {
          return Optional.of(UUID.fromString(sid));
        } catch (IllegalArgumentException e) {
          return Optional.empty();
        }
      }
    }
    return Optional.empty();
  }
}
