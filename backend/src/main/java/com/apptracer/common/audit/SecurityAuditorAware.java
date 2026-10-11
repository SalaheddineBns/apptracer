package com.apptracer.common.audit;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * RG-00-04 : identifiant de l'utilisateur courant, lu dans le {@code SecurityContext}.
 *
 * <p>Il s'appuie sur {@link Authentication#getName()}. Avec le JWT (access token dont {@code sub} =
 * identifiant de l'utilisateur), {@code JwtAuthenticationToken#getName()} renvoie le {@code sub} :
 * aucun changement ici au branchement du JWT. Sans authentification, ou si le nom n'est pas un
 * identifiant numérique, il n'y a pas d'auditeur ({@code NULL} : création par le système).
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
