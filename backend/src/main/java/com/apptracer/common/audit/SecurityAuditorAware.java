package com.apptracer.common.audit;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * RG-00-04: identifier of the current user, read from the {@code SecurityContext}.
 *
 * <p>It relies on {@link Authentication#getName()}. With the JWT (access token whose {@code sub} =
 * the user's identifier), {@code JwtAuthenticationToken#getName()} returns the {@code sub}: nothing
 * changes here when plugging in the JWT. Without authentication, or if the name is not a numeric
 * identifier, there is no auditor ({@code NULL}: created by the system).
 */
public class SecurityAuditorAware implements AuditorAware<Long> {

  @Override
  public Optional<Long> getCurrentAuditor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return Optional.empty();
    }
    try {
      return Optional.of(Long.valueOf(authentication.getName()));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}
