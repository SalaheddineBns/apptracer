package com.apptracer.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.security.AccessTokenService;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.http.MediaType;
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
        "UPDATE app_settings SET standard_day_minutes = 480, version = 0,"
            + " updated_at = NULL, updated_by = NULL WHERE id = 1");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  private String bearer(User user) {
    return "Bearer " + tokens.issue(user.getId(), user.getRole(), UUID.randomUUID());
  }

  @Test
  @DisplayName("CA-06-01 : une installation neuve renvoie 480 minutes à USER, MANAGER et ADMIN")
  void freshInstallationReturns480() throws Exception {
    for (User u : new User[] {plainUser, manager, admin}) {
      mockMvc
          .perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(u)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.standardDayMinutes").value(480))
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

  private static String body(Object minutes, long version) {
    return "{\"standardDayMinutes\":" + minutes + ",\"version\":" + version + "}";
  }

  private org.springframework.test.web.servlet.ResultActions putAs(User u, String body)
      throws Exception {
    return mockMvc.perform(
        put(URL)
            .header(HttpHeaders.AUTHORIZATION, bearer(u))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private int storedMinutes() {
    return jdbc.queryForObject("SELECT standard_day_minutes FROM app_settings", Integer.class);
  }

  @Test
  @DisplayName("CA-06-02 : l'ADMIN enregistre 450 minutes, la lecture renvoie son nom et la date")
  void adminUpdates() throws Exception {
    Instant before = Instant.now();
    putAs(admin, body(450, 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.standardDayMinutes").value(450))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.lastModifiedBy.id").value(admin.getId()));

    mockMvc
        .perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(jsonPath("$.standardDayMinutes").value(450))
        .andExpect(jsonPath("$.lastModifiedBy.firstName").value("Ada"))
        .andExpect(jsonPath("$.lastModifiedBy.lastName").value("Admin"))
        .andExpect(jsonPath("$.lastModifiedAt").isNotEmpty());
    Instant at =
        jdbc.queryForObject("SELECT updated_at FROM app_settings", java.sql.Timestamp.class)
            .toInstant();
    assertThat(Duration.between(before, at).abs()).isLessThan(Duration.ofMinutes(1));
    // RG-06-04 : un USER lit la nouvelle valeur, sans la trace
    mockMvc
        .perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(plainUser)))
        .andExpect(jsonPath("$.standardDayMinutes").value(450))
        .andExpect(jsonPath("$.lastModifiedBy").isEmpty());
  }

  @Test
  @DisplayName("CA-06-06 : 30, 45, 430, 1 455, 0 donnent 400 avec le champ en erreur, jamais 500")
  void invalidValuesAre400() throws Exception {
    for (int bad : new int[] {30, 45, 430, 1455, 1440 + 15, 0, -15}) {
      putAs(admin, body(bad, 0))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("standardDayMinutes"))
          .andExpect(
              jsonPath("$.errors[0].message")
                  .value(
                      "La journée standard doit être comprise entre 1 h et 24 h, par pas de 15 minutes."));
    }
    putAs(admin, "{\"version\":0}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("standardDayMinutes"));
    putAs(admin, body("\"abc\"", 0)).andExpect(status().isBadRequest());
    assertThat(storedMinutes()).isEqualTo(480);
  }

  @Test
  @DisplayName("RG-06-02 : les bornes 60 et 1 440 minutes sont acceptées")
  void boundsAccepted() throws Exception {
    putAs(admin, body(60, 0)).andExpect(status().isOk());
    putAs(admin, body(1440, 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.standardDayMinutes").value(1440));
  }

  @Test
  @DisplayName("CA-06-07 : un USER et un MANAGER reçoivent 403 sur la modification")
  void nonAdminForbidden() throws Exception {
    for (User u : new User[] {plainUser, manager}) {
      putAs(u, body(450, 0)).andExpect(status().isForbidden());
    }
    assertThat(storedMinutes()).isEqualTo(480);
  }

  @Test
  @DisplayName("CA-06-08 : sans jeton, la modification répond 401")
  void anonymousUpdateIs401() throws Exception {
    mockMvc
        .perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body(450, 0)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("RG-06-06 : une valeur identique ne change ni auteur, ni date, ni version")
  void identicalValueKeepsPreviousTrace() throws Exception {
    User other = users.save(new User("set-admin2@example.com", "Bob", "Autre", "hash", Role.ADMIN));
    putAs(admin, body(450, 0)).andExpect(status().isOk());
    Object[] before =
        jdbc.queryForObject(
            "SELECT updated_at, updated_by, version FROM app_settings",
            (rs, i) -> new Object[] {rs.getTimestamp(1), rs.getLong(2), rs.getLong(3)});

    putAs(other, body(450, 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.standardDayMinutes").value(450))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.lastModifiedBy.id").value(admin.getId()));

    Object[] after =
        jdbc.queryForObject(
            "SELECT updated_at, updated_by, version FROM app_settings",
            (rs, i) -> new Object[] {rs.getTimestamp(1), rs.getLong(2), rs.getLong(3)});
    assertThat(after).containsExactly(before);
  }

  @Test
  @DisplayName("Modification concurrente : une version périmée donne 409 sans rien écrire")
  void staleVersionIs409() throws Exception {
    putAs(admin, body(450, 0)).andExpect(status().isOk());
    putAs(admin, body(480, 0))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(storedMinutes()).isEqualTo(450);
  }
}
