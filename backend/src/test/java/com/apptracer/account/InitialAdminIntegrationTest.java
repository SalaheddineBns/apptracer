package com.apptracer.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.account.service.InitialAdminInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InitialAdminIntegrationTest {

  @Autowired private InitialAdminInitializer initializer;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder encoder;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mockMvc;

  @BeforeEach
  @AfterEach
  void clean() {
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  @Test
  @DisplayName(
      "RG-03-16 : l'ADMIN initial est créé depuis la configuration, par le système, et peut se connecter")
  void adminIsCreatedFromConfigurationAndCanLogin() throws Exception {
    assertThat(initializer.createIfAbsent()).isTrue();

    User admin = users.findByEmail("initial-admin@example.test").orElseThrow();
    assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    assertThat(admin.isActive()).isTrue();
    assertThat(admin.getCreatedBy()).isNull();
    assertThat(admin.getUpdatedBy()).isNull();
    assertThat(admin.getCreatedAt()).isNotNull();
    assertThat(admin.getPasswordHash()).startsWith("$2");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"initial-admin@example.test\",\"password\":\"Test-only-admin-pw-123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.role").value("ADMIN"));
  }

  @Test
  @DisplayName("RG-03-16 : un deuxième démarrage ne crée rien et ne réécrit pas le mot de passe")
  void secondRunIsIdempotent() {
    initializer.createIfAbsent();
    User admin = users.findByEmail("initial-admin@example.test").orElseThrow();
    String hash = admin.getPasswordHash();

    assertThat(initializer.createIfAbsent()).isFalse();

    assertThat(users.count()).isEqualTo(1);
    User after = users.findById(admin.getId()).orElseThrow();
    assertThat(after.getPasswordHash()).isEqualTo(hash);
    assertThat(after.getVersion()).isEqualTo(admin.getVersion());
  }

  @Test
  @DisplayName("RG-03-16 : si un autre ADMIN existe, aucun compte n'est créé")
  void existingAdminPreventsCreation() {
    users.save(
        new User(
            "other@example.test",
            "Other",
            "Admin",
            encoder.encode("Another-pw-12345"),
            Role.ADMIN));

    assertThat(initializer.createIfAbsent()).isFalse();

    assertThat(users.count()).isEqualTo(1);
    assertThat(users.findByEmail("initial-admin@example.test")).isEmpty();
  }
}
