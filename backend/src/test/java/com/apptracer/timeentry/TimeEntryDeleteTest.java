package com.apptracer.timeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.security.AccessTokenService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultActions;

/** RG-01-11: logical deletion of a time entry. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TimeEntryDeleteTest {

  private static final String URL = "/api/v1/time-entries";
  private static final String HOUR_BODY =
      "{\"granularity\":\"HOUR\",\"startDate\":\"2026-10-05\",\"startTime\":\"09:00\","
          + "\"endTime\":\"10:00\",\"activityTypeCode\":\"REUNION\"}";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  private User user;
  private User manager;
  private User admin;
  private User other;

  @BeforeEach
  void setUp() {
    user = users.save(new User("de-user@example.com", "U", "User", "hash", Role.USER));
    manager = users.save(new User("de-mgr@example.com", "M", "Mgr", "hash", Role.MANAGER));
    admin = users.save(new User("de-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    other = users.save(new User("de-other@example.com", "O", "Other", "hash", Role.USER));
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM time_entries");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  private String bearer(User u) {
    return "Bearer " + tokens.issue(u.getId(), u.getRole(), UUID.randomUUID());
  }

  private long create(User u, String body) throws Exception {
    mockMvc
        .perform(
            post(URL)
                .header(HttpHeaders.AUTHORIZATION, bearer(u))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
    return jdbc.queryForObject("SELECT max(id) FROM time_entries", Long.class);
  }

  private ResultActions remove(User u, long id) throws Exception {
    return mockMvc.perform(delete(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(u)));
  }

  private Map<String, Object> row(long id) {
    return jdbc.queryForMap("SELECT * FROM time_entries WHERE id = ?", id);
  }

  @Test
  @DisplayName(
      "RG-01-11 : le propriétaire supprime sa saisie (204), qui et quand en base, ligne conservée")
  void ownerDeletes() throws Exception {
    long id = create(user, HOUR_BODY);
    Instant before = clock.instant();
    remove(user, id).andExpect(status().isNoContent());
    Map<String, Object> row = row(id);
    assertThat(row.get("deleted_by")).isEqualTo(user.getId());
    Instant deletedAt = ((java.sql.Timestamp) row.get("deleted_at")).toInstant();
    assertThat(deletedAt).isBetween(before.minusSeconds(1), before.plus(Duration.ofMinutes(1)));
    assertThat(row.get("created_by")).isEqualTo(user.getId());
  }

  @Test
  @DisplayName("RG-01-11 : un MANAGER supprime sa propre saisie et y est tracé")
  void managerDeletesOwn() throws Exception {
    long id = create(manager, HOUR_BODY);
    remove(manager, id).andExpect(status().isNoContent());
    assertThat(row(id).get("deleted_by")).isEqualTo(manager.getId());
  }

  @Test
  @DisplayName(
      "CA-01-08 : RG-01-10, la saisie d'un autre est intouchable, 403 pour USER et MANAGER")
  void otherUsersEntry() throws Exception {
    long id = create(other, HOUR_BODY);
    remove(user, id)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-01-10"));
    remove(manager, id)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-01-10"));
    assertThat(row(id).get("deleted_at")).isNull();
    assertThat(row(id).get("deleted_by")).isNull();
  }

  @Test
  @DisplayName("RG-01-11 : une saisie supprimée devient invisible (lecture et modification en 404)")
  void deletedEntryIsInvisible() throws Exception {
    long id = create(user, HOUR_BODY);
    remove(user, id).andExpect(status().isNoContent());
    mockMvc
        .perform(get(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            put(URL + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(HOUR_BODY.replace("}", ",\"version\":0}")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "RG-01-11 : double suppression et saisie inconnue donnent 404, sans réécrire la trace")
  void secondDeleteIsNotFound() throws Exception {
    long id = create(user, HOUR_BODY);
    remove(user, id).andExpect(status().isNoContent());
    Object firstDeletedAt = row(id).get("deleted_at");
    remove(user, id).andExpect(status().isNotFound());
    remove(user, 999999).andExpect(status().isNotFound());
    assertThat(row(id).get("deleted_at")).isEqualTo(firstDeletedAt);
    // la saisie supprimée d'un autre reste 404 (et non 403) pour tout le monde
    remove(manager, id).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "RG-01-06 : après suppression, le même créneau peut être recréé (service et contrainte)")
  void slotCanBeRecreated() throws Exception {
    long id = create(user, HOUR_BODY);
    // tant qu'elle existe, le créneau est occupé
    mockMvc
        .perform(
            post(URL)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(HOUR_BODY))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-01-06"));
    remove(user, id).andExpect(status().isNoContent());
    long again = create(user, HOUR_BODY);
    assertThat(again).isNotEqualTo(id);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM time_entries", Integer.class))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("RG-01-14 : sans jeton 401, un ADMIN reçoit 403 RG-03-09, la saisie reste intacte")
  void accessControl() throws Exception {
    long id = create(user, HOUR_BODY);
    mockMvc.perform(delete(URL + "/" + id)).andExpect(status().isUnauthorized());
    remove(admin, id)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-03-09"));
    assertThat(row(id).get("deleted_at")).isNull();
  }
}
