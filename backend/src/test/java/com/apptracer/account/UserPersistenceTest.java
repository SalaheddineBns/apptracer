package com.apptracer.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.auth.domain.RefreshToken;
import com.apptracer.auth.domain.RevocationReason;
import com.apptracer.auth.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, UserPersistenceTest.ProbeConfig.class})
class UserPersistenceTest {

  @RestController
  static class DuplicateProbeController {
    private final UserRepository users;

    DuplicateProbeController(UserRepository users) {
      this.users = users;
    }

    @GetMapping("/api/v1/probe/duplicate-email")
    void duplicate() {
      users.saveAndFlush(new User("Dup@Example.com", "A", "B", "hash", Role.USER));
      users.saveAndFlush(new User("dup@example.COM", "C", "D", "hash", Role.USER));
    }
  }

  @TestConfiguration
  static class ProbeConfig {
    @Bean
    DuplicateProbeController duplicateProbeController(UserRepository users) {
      return new DuplicateProbeController(users);
    }
  }

  @Autowired private UserRepository users;
  @Autowired private RefreshTokenRepository refreshTokens;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mockMvc;

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  private static void authenticateAs(Long id) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(String.valueOf(id), null, List.of()));
  }

  @Test
  @DisplayName("RG-00-04 : created_at/by et updated_at/by sont alimentées via JPA sur User")
  void auditColumnsArePopulated() {
    // création par le système : pas d'auditeur
    User admin = users.saveAndFlush(new User("admin@example.com", "Ad", "Min", "hash", Role.ADMIN));
    assertThat(admin.getCreatedAt()).isNotNull();
    assertThat(admin.getUpdatedAt()).isNotNull();
    assertThat(admin.getCreatedBy()).isNull();
    assertThat(admin.getUpdatedBy()).isNull();

    // création par un utilisateur authentifié
    authenticateAs(admin.getId());
    User bob = users.saveAndFlush(new User("bob@example.com", "Bob", "Dupont", "hash", Role.USER));
    assertThat(bob.getCreatedBy()).isEqualTo(admin.getId());
    assertThat(bob.getUpdatedBy()).isEqualTo(admin.getId());

    // modification par un autre utilisateur : seuls updated_* changent
    authenticateAs(bob.getId());
    User loaded = users.findById(bob.getId()).orElseThrow();
    loaded.setLastName("Martin");
    User saved = users.saveAndFlush(loaded);
    assertThat(saved.getCreatedBy()).isEqualTo(admin.getId());
    assertThat(saved.getCreatedAt()).isEqualTo(bob.getCreatedAt());
    assertThat(saved.getUpdatedBy()).isEqualTo(bob.getId());
    assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(bob.getUpdatedAt());
    assertThat(saved.getVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("RG-03-01 : l'index unique refuse un e-mail déjà utilisé, quelle que soit la casse")
  void emailUniqueIgnoringCase() {
    users.saveAndFlush(new User("Alice@Example.com", "Alice", "A", "hash", Role.USER));
    assertThat(users.findByEmail("alice@example.COM")).isPresent();
    assertThat(users.existsByEmail("ALICE@example.com")).isTrue();
    assertThatThrownBy(
            () -> users.saveAndFlush(new User("alice@example.com", "Al", "B", "hash", Role.USER)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @WithMockUser
  @DisplayName("RG-03-01 : la violation de l'index unique e-mail est traduite en 409 RG-03-01")
  void duplicateEmailIsTranslatedTo409() throws Exception {
    mockMvc
        .perform(get("/api/v1/probe/duplicate-email"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-03-01"))
        .andExpect(jsonPath("$.errors[0].field").value("email"));
  }

  @Test
  @DisplayName("RG-03-10 : le rôle est restreint à USER, MANAGER, ADMIN")
  void roleIsConstrained() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO users (email, first_name, last_name, password_hash, role, created_at, updated_at)"
                        + " VALUES ('x@y.fr','a','b','h','ROOT', now(), now())"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("RG-03-07 : un refresh token est persisté puis révoqué avec sa raison")
  void refreshTokenRoundTrip() {
    User user = users.saveAndFlush(new User("rt@example.com", "R", "T", "hash", Role.USER));
    String hash = "a".repeat(64);
    Instant now = Instant.now();
    RefreshToken token =
        refreshTokens.saveAndFlush(
            new RefreshToken(user.getId(), UUID.randomUUID(), hash, now, now.plusSeconds(60)));

    RefreshToken found = refreshTokens.findByTokenHash(hash).orElseThrow();
    assertThat(found.getId()).isEqualTo(token.getId());
    assertThat(found.getRevokedAt()).isNull();

    found.revoke(now, RevocationReason.ROTATED);
    refreshTokens.saveAndFlush(found);
    assertThat(refreshTokens.findByTokenHash(hash).orElseThrow().getRevocationReason())
        .isEqualTo(RevocationReason.ROTATED);

    assertThatThrownBy(
            () ->
                refreshTokens.saveAndFlush(
                    new RefreshToken(
                        user.getId(), UUID.randomUUID(), hash, now, now.plusSeconds(60))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
