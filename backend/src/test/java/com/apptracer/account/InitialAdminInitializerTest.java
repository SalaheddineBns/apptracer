package com.apptracer.account;

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
import com.apptracer.account.service.BootstrapAdminProperties;
import com.apptracer.account.service.InitialAdminInitializer;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class InitialAdminInitializerTest {

  private static final String PASSWORD = "Secret-admin-pw-42";

  private final UserRepository users = mock(UserRepository.class);
  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

  private InitialAdminInitializer initializer(
      String email, String password, String first, String last) {
    return new InitialAdminInitializer(
        users,
        encoder,
        new BootstrapAdminProperties(email, password, first, last),
        Validation.buildDefaultValidatorFactory().getValidator());
  }

  private InitialAdminInitializer valid() {
    return initializer("admin@example.com", PASSWORD, "Ada", "Admin");
  }

  @Test
  @DisplayName("RG-03-16 : sans compte ADMIN, un ADMIN actif est créé avec un mot de passe BCrypt")
  void createsAdminWhenNoneExists() {
    when(users.existsByRole(Role.ADMIN)).thenReturn(false);

    assertThat(valid().createIfAbsent()).isTrue();

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(users).save(captor.capture());
    User admin = captor.getValue();
    assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    assertThat(admin.isActive()).isTrue();
    assertThat(admin.getEmail()).isEqualTo("admin@example.com");
    assertThat(admin.getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
    assertThat(encoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
  }

  @Test
  @DisplayName("RG-03-16 : si un ADMIN existe déjà, rien n'est fait (idempotent)")
  void doesNothingWhenAdminExists() {
    when(users.existsByRole(Role.ADMIN)).thenReturn(true);

    // even with an invalid configuration: no validation, no write access
    assertThat(initializer("", "", "", "").createIfAbsent()).isFalse();

    verify(users, never()).save(any());
    verify(users, never()).existsByEmail(any());
  }

  @Test
  @DisplayName("RG-03-16 : configuration absente, le démarrage échoue avec un message explicite")
  void missingConfigurationFails() {
    assertThatThrownBy(() -> initializer(null, null, null, null).createIfAbsent())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ADMIN_EMAIL")
        .hasMessageContaining("ADMIN_PASSWORD")
        .hasMessageContaining("ADMIN_FIRST_NAME")
        .hasMessageContaining("ADMIN_LAST_NAME");
    verify(users, never()).save(any());
  }

  @Test
  @DisplayName("RG-03-02 : mot de passe trop faible, échec sans afficher le mot de passe")
  void weakPasswordFailsWithoutLeakingIt() {
    String weak = "faible1";
    assertThatThrownBy(
            () -> initializer("admin@example.com", weak, "Ada", "Admin").createIfAbsent())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ADMIN_PASSWORD")
        .hasMessageNotContaining(weak);
    verify(users, never()).save(any());
  }

  @Test
  @DisplayName("RG-03-01 : e-mail au format invalide, échec explicite")
  void invalidEmailFails() {
    assertThatThrownBy(() -> initializer("pas-un-email", PASSWORD, "Ada", "Admin").createIfAbsent())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ADMIN_EMAIL")
        .hasMessageNotContaining(PASSWORD);
  }

  @Test
  @DisplayName("RG-03-16 : e-mail d'un compte existant non ADMIN, échec sans modifier ce compte")
  void emailOfExistingAccountFails() {
    when(users.existsByEmail("admin@example.com")).thenReturn(true);

    assertThatThrownBy(() -> valid().createIfAbsent())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ADMIN_EMAIL")
        .hasMessageNotContaining(PASSWORD);
    verify(users, never()).save(any());
  }

  @Test
  @DisplayName(
      "RG-03-16 : la représentation texte de la configuration n'expose pas le mot de passe")
  void propertiesToStringHidesPassword() {
    assertThat(new BootstrapAdminProperties("a@b.fr", PASSWORD, "A", "B").toString())
        .doesNotContain(PASSWORD);
  }
}
