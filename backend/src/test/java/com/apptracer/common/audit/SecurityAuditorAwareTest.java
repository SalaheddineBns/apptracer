package com.apptracer.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityAuditorAwareTest {

  private final SecurityAuditorAware auditorAware = new SecurityAuditorAware();

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("RG-00-04 : l'auditeur est l'identifiant de l'utilisateur authentifié")
  void returnsAuthenticatedUserId() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("42", null, List.of()));
    assertThat(auditorAware.getCurrentAuditor()).contains(42L);
  }

  @Test
  @DisplayName("RG-00-04 : sans authentification, pas d'auditeur (système)")
  void noAuthenticationMeansNoAuditor() {
    assertThat(auditorAware.getCurrentAuditor()).isEmpty();
  }

  @Test
  @DisplayName("RG-00-04 : un utilisateur anonyme n'est pas un auditeur")
  void anonymousMeansNoAuditor() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    assertThat(auditorAware.getCurrentAuditor()).isEmpty();
  }

  @Test
  @DisplayName("RG-00-04 : un nom non numérique n'est pas un identifiant d'auditeur")
  void nonNumericNameMeansNoAuditor() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("user", null, List.of()));
    assertThat(auditorAware.getCurrentAuditor()).isEmpty();
  }
}
