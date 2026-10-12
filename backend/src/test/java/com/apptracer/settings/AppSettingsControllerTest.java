package com.apptracer.settings;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.security.AccessTokenService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AppSettingsControllerTest {

  private static final String URL = "/api/v1/app-settings";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User admin;
  private User plainUser;
  private User manager;

  @BeforeEach
  void setUp() {
    admin = users.save(new User("set-admin@example.com", "Ada", "Admin", "hash", Role.ADMIN));
    plainUser = users.save(new User("set-user@example.com", "U", "User", "hash", Role.USER));
    manager = users.save(new User("set-mgr@example.com", "M", "Mgr", "hash", Role.MANAGER));
  }

  @AfterEach
  void cleanUp() {
    // la ligne est remise à l'état d'installation avant de supprimer les comptes (clé étrangère)
    jdbc.update(
        "UPDATE app_settings SET standard_day_minutes = 420, version = 0,"
            + " updated_at = NULL, updated_by = NULL WHERE id = 1");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  private String bearer(User user) {
    return "Bearer " + tokens.issue(user.getId(), user.getRole(), UUID.randomUUID());
  }

  @Test
  @DisplayName("CA-06-01 : une installation neuve renvoie 420 minutes à USER, MANAGER et ADMIN")
  void freshInstallationReturns420() throws Exception {
    for (User u : new User[] {plainUser, manager, admin}) {
      mockMvc
          .perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(u)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.standardDayMinutes").value(420))
          .andExpect(jsonPath("$.version").value(0))
          .andExpect(jsonPath("$.lastModifiedAt").isEmpty())
          .andExpect(jsonPath("$.lastModifiedBy").isEmpty());
    }
  }

  @Test
  @DisplayName("CA-06-08 : sans jeton, l'API des paramètres répond 401")
  void anonymousIsRejected() throws Exception {
    mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "RG-06-05 : seul l'ADMIN reçoit l'auteur et la date de la dernière modification (H7)")
  void lastModificationOnlyForAdmin() throws Exception {
    jdbc.update(
        "UPDATE app_settings SET standard_day_minutes = 450, version = 1,"
            + " updated_at = '2026-05-04T08:30:00Z', updated_by = ? WHERE id = 1",
        admin.getId());

    mockMvc
        .perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.standardDayMinutes").value(450))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.lastModifiedAt").value("2026-05-04T08:30:00Z"))
        .andExpect(jsonPath("$.lastModifiedBy.id").value(admin.getId()))
        .andExpect(jsonPath("$.lastModifiedBy.firstName").value("Ada"))
        .andExpect(jsonPath("$.lastModifiedBy.lastName").value("Admin"))
        .andExpect(jsonPath("$.lastModifiedBy.email").doesNotExist());

    for (User u : new User[] {plainUser, manager}) {
      mockMvc
          .perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(u)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.standardDayMinutes").value(450))
          .andExpect(jsonPath("$.lastModifiedAt").isEmpty())
          .andExpect(jsonPath("$.lastModifiedBy").isEmpty());
    }
  }
}
