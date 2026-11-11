package com.apptracer.timeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.domain.Project;
import com.apptracer.referential.domain.TagColor;
import com.apptracer.referential.repository.ActivityTypeRepository;
import com.apptracer.referential.repository.ProjectRepository;
import com.apptracer.security.AccessTokenService;
import com.apptracer.timeentry.domain.TimeEntry;
import com.apptracer.timeentry.repository.TimeEntryRepository;
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

/** RG-01-12: update of a time entry. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TimeEntryUpdateTest {

  private static final String URL = "/api/v1/time-entries";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private ActivityTypeRepository types;
  @Autowired private ProjectRepository projects;
  @Autowired private TimeEntryRepository entries;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User user;
  private User manager;
  private User admin;
  private User other;

  @BeforeEach
  void setUp() {
    user = users.save(new User("up-user@example.com", "U", "User", "hash", Role.USER));
    manager = users.save(new User("up-mgr@example.com", "M", "Mgr", "hash", Role.MANAGER));
    admin = users.save(new User("up-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    other = users.save(new User("up-other@example.com", "O", "Other", "hash", Role.USER));
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM time_entries");
    jdbc.execute(
        "UPDATE app_settings SET standard_day_minutes = 480, version = 0,"
            + " updated_at = NULL, updated_by = NULL");
    jdbc.execute(
        "DELETE FROM activity_types WHERE code NOT IN"
            + " ('TRAVAIL_PROJET','REUNION','LECTURE_SFD','REVUE_MR','FORMATION','AUTRE')");
    jdbc.execute("DELETE FROM projects");
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

  private ResultActions update(User u, long id, String body) throws Exception {
    return mockMvc.perform(
        put(URL + "/" + id)
            .header(HttpHeaders.ACCEPT_LANGUAGE, "fr")
            .header(HttpHeaders.AUTHORIZATION, bearer(u))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private static String hour(String date, String from, String to, long version) {
    return ("{\"granularity\":\"HOUR\",\"startDate\":\"%s\",\"startTime\":\"%s\","
            + "\"endTime\":\"%s\",\"activityTypeCode\":\"REUNION\",\"version\":%d}")
        .formatted(date, from, to, version);
  }

  private static String day(String date, long version) {
    return ("{\"granularity\":\"DAY\",\"startDate\":\"%s\",\"activityTypeCode\":\"REUNION\","
            + "\"version\":%d}")
        .formatted(date, version);
  }

  private static String range(String from, String to, long version) {
    return ("{\"granularity\":\"RANGE\",\"startDate\":\"%s\",\"endDate\":\"%s\","
            + "\"activityTypeCode\":\"FORMATION\",\"version\":%d}")
        .formatted(from, to, version);
  }

  private static String createHour(String date, String from, String to) {
    return hour(date, from, to, 0).replace(",\"version\":0", "");
  }

  private Map<String, Object> row(long id) {
    return jdbc.queryForMap("SELECT * FROM time_entries WHERE id = ?", id);
  }

  @Test
  @DisplayName(
      "RG-01-12 : le propriétaire modifie sa saisie, durée recalculée, version incrémentée")
  void ownerUpdates() throws Exception {
    long id = create(user, createHour("2026-10-05", "09:00", "10:00"));
    update(user, id, hour("2026-10-06", "14:00", "16:30", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.startDate").value("2026-10-06"))
        .andExpect(jsonPath("$.startTime").value("14:00"))
        .andExpect(jsonPath("$.durationMinutes").value(150))
        .andExpect(jsonPath("$.version").value(1));
    // RG-01-11, RG-00-04 : modifié par / le, créé par inchangé
    TimeEntry e = entries.findById(id).orElseThrow();
    assertThat(e.getUpdatedBy()).isEqualTo(user.getId());
    assertThat(e.getCreatedBy()).isEqualTo(user.getId());
    assertThat(e.getUpdatedAt()).isAfterOrEqualTo(e.getCreatedAt());
  }

  @Test
  @DisplayName("RG-01-11 : l'auteur de la dernière modification est tracé")
  void updaterIsTraced() throws Exception {
    long id = create(manager, createHour("2026-10-05", "09:00", "10:00"));
    update(manager, id, hour("2026-10-05", "09:00", "11:00", 0)).andExpect(status().isOk());
    assertThat(row(id).get("updated_by")).isEqualTo(manager.getId());
  }

  @Test
  @DisplayName("CA-01-08 : la saisie d'un autre USER n'est pas modifiable, 403 RG-01-10")
  void otherUsersEntry() throws Exception {
    long id = create(other, createHour("2026-10-05", "09:00", "10:00"));
    update(user, id, hour("2026-10-05", "13:00", "14:00", 0))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-01-10"));
    // y compris pour un MANAGER
    update(manager, id, hour("2026-10-05", "13:00", "14:00", 0))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-01-10"));
    assertThat(row(id).get("start_time").toString()).isEqualTo("09:00:00");
    assertThat(row(id).get("version")).isEqualTo(0L);
  }

  @Test
  @DisplayName("RG-01-12 : une modification qui recoupe l'ancien créneau de la saisie est acceptée")
  void selfOverlapAccepted() throws Exception {
    long id = create(user, createHour("2026-10-05", "09:00", "10:00"));
    update(user, id, hour("2026-10-05", "09:30", "10:30", 0)).andExpect(status().isOk());
    // identique à elle-même
    update(user, id, hour("2026-10-05", "09:30", "10:30", 1)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("CA-01-04 : RG-01-06, la modification qui recoupe une autre saisie donne 409")
  void overlapWithAnotherRejected() throws Exception {
    create(user, createHour("2026-10-05", "09:00", "10:00"));
    long id = create(user, createHour("2026-10-05", "10:00", "11:00"));
    update(user, id, hour("2026-10-05", "09:45", "10:30", 0))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-01-06"))
        .andExpect(jsonPath("$.detail").value(containsString("05/10/2026")));
    assertThat(row(id).get("start_time").toString()).isEqualTo("10:00:00");
    // contiguë : acceptée
    update(user, id, hour("2026-10-05", "10:00", "10:15", 0)).andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "RG-01-12 : HOUR vers DAY puis vers RANGE, heures et date de fin remises en cohérence")
  void granularityHourToDayToRange() throws Exception {
    long id = create(user, createHour("2026-10-05", "09:00", "10:00"));
    update(user, id, day("2026-10-05", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.granularity").value("DAY"))
        .andExpect(jsonPath("$.durationMinutes").value(480))
        .andExpect(jsonPath("$.startTime").doesNotExist())
        .andExpect(jsonPath("$.endDate").doesNotExist());
    assertThat(row(id).get("start_time")).isNull();
    assertThat(row(id).get("end_time")).isNull();
    update(user, id, range("2026-10-05", "2026-10-16", 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.durationMinutes").value(4800))
        .andExpect(jsonPath("$.endDate").value("2026-10-16"));
  }

  @Test
  @DisplayName("RG-01-12 : RANGE vers HOUR, la date de fin redevient celle du début")
  void granularityRangeToHour() throws Exception {
    long id =
        create(
            user,
            "{\"granularity\":\"RANGE\",\"startDate\":\"2026-10-05\",\"endDate\":\"2026-10-09\","
                + "\"activityTypeCode\":\"FORMATION\"}");
    update(user, id, hour("2026-10-07", "09:00", "10:00", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.granularity").value("HOUR"))
        .andExpect(jsonPath("$.endDate").doesNotExist())
        .andExpect(jsonPath("$.durationMinutes").value(60));
    assertThat(row(id).get("end_date").toString()).isEqualTo("2026-10-07");
  }

  @Test
  @DisplayName("RG-01-12 : les mêmes contrôles qu'à la création (heures, plage, date future)")
  void sameControlsAsCreation() throws Exception {
    long id = create(user, createHour("2026-10-05", "09:00", "10:00"));
    update(user, id, hour("2026-10-05", "10:00", "09:00", 0))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-01-01"));
    update(user, id, range("2026-09-01", "2026-10-02", 0))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-01-03")); // 32 jours
    update(user, id, day("2099-01-01", 0))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-01-07"));
    update(user, id, "{\"granularity\":\"HOUR\",\"startDate\":\"2026-10-05\",\"version\":0}")
        .andExpect(status().isBadRequest());
    update(user, id, day("2026-10-05", 0).replace(",\"version\":0", ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("version"));
  }

  @Test
  @DisplayName("RG-01-12 : une version périmée donne 409 sans code, la saisie reste intacte")
  void staleVersion() throws Exception {
    long id = create(user, createHour("2026-10-05", "09:00", "10:00"));
    update(user, id, hour("2026-10-05", "09:00", "11:00", 0)).andExpect(status().isOk());
    update(user, id, hour("2026-10-05", "12:00", "13:00", 0))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").doesNotExist())
        .andExpect(jsonPath("$.type").value("https://apptracer/errors/concurrent-modification"));
    assertThat(row(id).get("start_time").toString()).isEqualTo("09:00:00");
  }

  @Test
  @DisplayName(
      "CA-06-04 : RG-06-04, modifier après un changement de journée standard refige la durée")
  void standardDayRefrozenOnUpdate() throws Exception {
    long id =
        create(
            user,
            "{\"granularity\":\"DAY\",\"startDate\":\"2026-10-05\","
                + "\"activityTypeCode\":\"REUNION\"}");
    assertThat(row(id).get("duration_minutes")).isEqualTo(480);
    jdbc.update("UPDATE app_settings SET standard_day_minutes = 450");
    // tant qu'elle n'est pas modifiée, la saisie garde 480
    assertThat(row(id).get("standard_day_minutes")).isEqualTo(480);
    update(user, id, day("2026-10-05", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.durationMinutes").value(450));
    assertThat(row(id).get("standard_day_minutes")).isEqualTo(450);
  }

  @Test
  @DisplayName(
      "RG-02-06 : un type devenu inactif est refusé à la modification, un autre actif accepté")
  void inactiveTypeRefusedOnUpdate() throws Exception {
    ActivityType t = types.saveAndFlush(new ActivityType("TMP_T", "Temp", false, TagColor.INFO));
    long id =
        create(
            user,
            "{\"granularity\":\"DAY\",\"startDate\":\"2026-10-05\",\"activityTypeCode\":\"TMP_T\"}");
    t.setActive(false);
    types.saveAndFlush(t);
    String sameType =
        "{\"granularity\":\"DAY\",\"startDate\":\"2026-10-05\","
            + "\"activityTypeCode\":\"TMP_T\",\"version\":0}";
    update(user, id, sameType)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-01-04"))
        .andExpect(jsonPath("$.errors[0].field").value("activityTypeCode"));
    update(user, id, day("2026-10-05", 0)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("RG-02-06 : un projet devenu inactif est refusé à la modification (code inchangé)")
  void inactiveProjectRefusedOnUpdate() throws Exception {
    Project p = projects.saveAndFlush(new Project("P1", "Projet un"));
    String withProject =
        "{\"granularity\":\"DAY\",\"startDate\":\"2026-10-05\","
            + "\"activityTypeCode\":\"TRAVAIL_PROJET\",\"projectCode\":\"P1\"";
    long id = create(user, withProject + "}");
    p.setActive(false);
    projects.saveAndFlush(p);
    update(user, id, withProject + ",\"version\":0}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-02-06"))
        .andExpect(jsonPath("$.errors[0].field").value("projectCode"));
  }

  @Test
  @DisplayName("RG-01-05 : changer de type vers un type sans projet retire le projet")
  void switchingTypeDropsProject() throws Exception {
    projects.saveAndFlush(new Project("P1", "Projet un"));
    long id =
        create(
            user,
            "{\"granularity\":\"DAY\",\"startDate\":\"2026-10-05\","
                + "\"activityTypeCode\":\"TRAVAIL_PROJET\",\"projectCode\":\"P1\"}");
    update(user, id, day("2026-10-05", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.project").doesNotExist());
    assertThat(row(id).get("project_id")).isNull();
  }

  @Test
  @DisplayName("RG-01-11 : saisie inconnue ou supprimée donne 404")
  void unknownOrDeleted() throws Exception {
    update(user, 999999, day("2026-10-05", 0)).andExpect(status().isNotFound());
    long id = create(user, createHour("2026-10-05", "09:00", "10:00"));
    TimeEntry e = entries.findById(id).orElseThrow();
    e.markDeleted(user.getId(), Instant.now());
    entries.saveAndFlush(e);
    update(user, id, day("2026-10-05", 0)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("RG-01-14 : sans jeton 401, un ADMIN reçoit 403")
  void accessControl() throws Exception {
    mockMvc
        .perform(
            put(URL + "/1").contentType(MediaType.APPLICATION_JSON).content(day("2026-10-05", 0)))
        .andExpect(status().isUnauthorized());
    update(admin, 1, day("2026-10-05", 0))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-03-09"));
  }
}
