package com.apptracer.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.auth.service.PasswordChangeService;
import com.apptracer.auth.service.RefreshTokenService;
import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.InvalidCredentialsException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordChangeServiceTest {

  private static final String OLD = "Ancien-motdepasse-1";
  private static final String NEW = "Nouveau-motdepasse-2";

  private final UserRepository users = mock(UserRepository.class);
  private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private final PasswordChangeService service =
      new PasswordChangeService(users, encoder, refreshTokens);
  private User user;

  @BeforeEach
  void setUp() {
    user = new User("jean@example.com", "Jean", "Dupont", encoder.encode(OLD), Role.USER);
    when(users.findById(5L)).thenReturn(Optional.of(user));
  }

  @Test
  @DisplayName("RG-03-15 : un ancien mot de passe incorrect est refusé, rien n'est modifié")
  void wrongCurrentPasswordIsRejected() {
    String hash = user.getPasswordHash();
    assertThatThrownBy(() -> service.changePassword(5L, UUID.randomUUID(), "mauvais-1234567", NEW))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getRgCode()).isEqualTo("RG-03-15");
              assertThat(e.getErrors()).extracting("field").containsExactly("currentPassword");
            });
    assertThat(user.getPasswordHash()).isEqualTo(hash);
    verify(users, never()).saveAndFlush(any());
    verify(refreshTokens, never()).revokeOtherSessions(any(), any());
  }

  @Test
  @DisplayName("RG-03-03 : le nouveau mot de passe est stocké haché en BCrypt")
  void newPasswordIsHashed() {
    service.changePassword(5L, UUID.randomUUID(), OLD, NEW);
    assertThat(user.getPasswordHash()).startsWith("$2").doesNotContain(NEW);
    assertThat(encoder.matches(NEW, user.getPasswordHash())).isTrue();
    assertThat(encoder.matches(OLD, user.getPasswordHash())).isFalse();
    verify(users).saveAndFlush(user);
  }

  @Test
  @DisplayName(
      "RG-03-15 : le changement révoque les autres sessions, en gardant la session courante")
  void otherSessionsAreRevoked() {
    UUID sid = UUID.randomUUID();
    service.changePassword(5L, sid, OLD, NEW);
    verify(refreshTokens).revokeOtherSessions(5L, sid);
  }

  @Test
  @DisplayName("Compte inexistant : échec d'authentification")
  void unknownUserFails() {
    when(users.findById(9L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.changePassword(9L, null, OLD, NEW))
        .isInstanceOf(InvalidCredentialsException.class);
  }
}
