package com.apptracer.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.auth.domain.RefreshToken;
import com.apptracer.auth.repository.RefreshTokenRepository;
import com.apptracer.auth.service.AuthService;
import com.apptracer.auth.service.LoginResult;
import com.apptracer.auth.service.RefreshTokenService;
import com.apptracer.common.error.InvalidCredentialsException;
import com.apptracer.security.AccessTokenService;
import com.apptracer.security.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-12T08:00:00Z");

  private final UserRepository users = mock(UserRepository.class);
  private final RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
  private final AccessTokenService accessTokens = mock(AccessTokenService.class);
  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private AuthService service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneId.of("Europe/Paris"));
    JwtProperties props =
        new JwtProperties(
            "0123456789abcdef0123456789abcdef", Duration.ofMinutes(15), Duration.ofDays(7));
    service =
        new AuthService(
            users, encoder, accessTokens, new RefreshTokenService(refreshRepo, props, clock));
  }

  private User user(boolean active, Role role) throws Exception {
    User u =
        new User("jean@example.com", "Jean", "Dupont", encoder.encode("Motdepasse12345"), role);
    u.setActive(active);
    var id = User.class.getDeclaredField("id");
    id.setAccessible(true);
    id.set(u, 5L);
    return u;
  }

  @Test
  @DisplayName("RG-03-03 : le mot de passe est comparé au haché BCrypt")
  void comparesAgainstBcryptHash() throws Exception {
    User u = user(true, Role.USER);
    assertThat(u.getPasswordHash()).startsWith("$2").doesNotContain("Motdepasse12345");
    when(users.findByEmail("jean@example.com")).thenReturn(Optional.of(u));
    when(accessTokens.issue(eq(5L), eq(Role.USER), any())).thenReturn("access");

    LoginResult result = service.login("jean@example.com", "Motdepasse12345");
    assertThat(result.user()).isSameAs(u);
    assertThatThrownBy(() -> service.login("jean@example.com", "autre-mot-de-passe-1"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("RG-03-04 : e-mail inconnu et mot de passe incorrect lèvent la même exception")
  void unknownEmailAndWrongPasswordAreIndistinguishable() throws Exception {
    when(users.findByEmail("jean@example.com")).thenReturn(Optional.of(user(true, Role.USER)));
    when(users.findByEmail("inconnu@example.com")).thenReturn(Optional.empty());

    Throwable wrongPassword =
        org.assertj.core.api.Assertions.catchThrowable(
            () -> service.login("jean@example.com", "mauvais-mot-de-passe-1"));
    Throwable unknownEmail =
        org.assertj.core.api.Assertions.catchThrowable(
            () -> service.login("inconnu@example.com", "Motdepasse12345"));

    assertThat(wrongPassword).isInstanceOf(InvalidCredentialsException.class);
    assertThat(unknownEmail)
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage(wrongPassword.getMessage());
    verify(refreshRepo, never()).save(any());
  }

  @Test
  @DisplayName("RG-03-05 : un compte inactif est refusé avec la même exception")
  void inactiveAccountIsRejected() throws Exception {
    when(users.findByEmail("jean@example.com")).thenReturn(Optional.of(user(false, Role.USER)));
    assertThatThrownBy(() -> service.login("jean@example.com", "Motdepasse12345"))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Identifiants invalides");
    verify(refreshRepo, never()).save(any());
  }

  @Test
  @DisplayName("RG-03-06 : l'access token est émis avec l'identifiant et le rôle du compte")
  void accessTokenCarriesIdAndRole() throws Exception {
    when(users.findByEmail("jean@example.com")).thenReturn(Optional.of(user(true, Role.MANAGER)));
    when(accessTokens.issue(eq(5L), eq(Role.MANAGER), any(UUID.class))).thenReturn("access-jwt");

    assertThat(service.login("jean@example.com", "Motdepasse12345").accessToken())
        .isEqualTo("access-jwt");
  }

  @Test
  @DisplayName("RG-03-07 : le refresh token est stocké haché, avec la durée JWT_REFRESH_TTL")
  void refreshTokenIsStoredHashed() throws Exception {
    when(users.findByEmail("jean@example.com")).thenReturn(Optional.of(user(true, Role.USER)));

    LoginResult result = service.login("jean@example.com", "Motdepasse12345");

    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshRepo).save(captor.capture());
    RefreshToken stored = captor.getValue();
    assertThat(result.refreshToken()).isNotBlank();
    assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(result.refreshToken());
    assertThat(stored.getUserId()).isEqualTo(5L);
    assertThat(stored.getIssuedAt()).isEqualTo(NOW);
    assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
  }
}
