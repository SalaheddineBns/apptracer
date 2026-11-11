package com.apptracer.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** The current user always comes from the token (claim {@code sub}), never from a parameter. */
@Component
public class CurrentUserProvider {

  /** Identifier of the authenticated user. */
  public Long currentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      throw new IllegalStateException("Aucun utilisateur authentifié");
    }
    return Long.valueOf(authentication.getName());
  }

  /** True if the authenticated user has the ADMIN role (authority {@code ROLE_ADMIN}). */
  public boolean isAdmin() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
  }

  /** Identifier of the current session (claim {@code sid} of the access token), if present. */
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
