package com.apptracer.timeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** RG-01-13 : journal d'une semaine. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TimeEntryWeekTest {

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
    user = users.save(new User("wk-user@example.com", "U", "User", "hash", Role.USER));
    manager = users.save(new User("wk-mgr@example.com", "M", "Mgr", "hash", Role.MANAGER));
    admin = users.save(new User("wk-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    other = users.save(new User("wk-other@example.com", "O", "Other", "hash", Role.USER));
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

  private ResultActions week(User u, String weekStart) throws Exception {
    return mockMvc.perform(
        get(URL).param("weekStart", weekStart).header(HttpHeaders.AUTHORIZATION, bearer(u)));
  }

  private static String hour(String date, String from, String to) {
    return ("{\"granularity\":\"HOUR\",\"startDate\":\"%s\",\"startTime\":\"%s\","
            + "\"endTime\":\"%s\",\"activityTypeCode\":\"REUNION\"}")
        .formatted(date, from, to);
  }

  private static String day(String date) {
    return "{\"granularity\":\"DAY\",\"startDate\":\"%s\",\"activityTypeCode\":\"REUNION\"}"
        .formatted(date);
  }

  private static String range(String from, String to) {
    return ("{\"granularity\":\"RANGE\",\"startDate\":\"%s\",\"endDate\":\"%s\","
            + "\"activityTypeCode\":\"FORMATION\"}")
        .formatted(from, to);
  }

  @Test
  @DisplayName("CA-01-09 : des saisies de 90 min et 8 h donnent un total de 570 min (9,5 h)")
  void totalOfTheWeek() throws Exception {
    create(user, hour("2026-10-05", "09:00", "10:30"));
    create(user, day("2026-10-06"));
    week(user, "2026-10-05")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weekStart").value("2026-10-05"))
        .andExpect(jsonPath("$.weekEnd").value("2026-10-11"))
        .andExpect(jsonPath("$.totalMinutes").value(570))
        .andExpect(jsonPath("$.entries.length()").value(2));
  }

  @Test
  @DisplayName("RG-01-13 : semaine vide, total 0 et liste vide")
  void emptyWeek() throws Exception {
    week(user, "2026-10-05")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalMinutes").value(0))
        .andExpect(jsonPath("$.entries").isEmpty());
  }

  @Test
  @DisplayName(
      "RG-01-13 : une PLAGE du jeudi au mardi suivant figure dans les deux semaines, 960 min chacune")
  void rangeAcrossTwoWeeks() throws Exception {
    create(user, range("2026-10-08", "2026-10-13"));
    week(user, "2026-10-05")
        .andExpect(jsonPath("$.totalMinutes").value(960))
        .andExpect(jsonPath("$.entries.length()").value(1))
        .andExpect(jsonPath("$.entries[0].endDate").value("2026-10-13"));
    week(user, "2026-10-12")
        .andExpect(jsonPath("$.totalMinutes").value(960))
        .andExpect(jsonPath("$.entries.length()").value(1));
    week(user, "2026-10-19").andExpect(jsonPath("$.entries").isEmpty());
    week(user, "2026-09-28").andExpect(jsonPath("$.entries").isEmpty());
  }

  @Test
  @DisplayName(
      "RG-01-13 : une PLAGE sur un week-end est listée mais compte 0 ; une JOURNEE un samedi compte 480")
  void weekendEntries() throws Exception {
    create(user, range("2026-10-10", "2026-10-11"));
    week(user, "2026-10-05")
        .andExpect(jsonPath("$.totalMinutes").value(0))
        .andExpect(jsonPath("$.entries.length()").value(1));
    jdbc.execute("DELETE FROM time_entries");
    create(user, day("2026-10-10"));
    week(user, "2026-10-05").andExpect(jsonPath("$.totalMinutes").value(480));
  }

  @Test
  @DisplayName("RG-01-13 : dimanche et lundi aux bornes, la semaine voisine ne les contient pas")
  void weekBounds() throws Exception {
    create(user, hour("2026-10-04", "09:00", "10:00")); // dimanche précédent
    create(user, hour("2026-10-05", "09:00", "10:00")); // lundi
    create(user, hour("2026-10-11", "09:00", "10:00")); // dimanche
    create(user, hour("2026-10-12", "09:00", "10:00")); // lundi suivant
    week(user, "2026-10-05")
        .andExpect(jsonPath("$.totalMinutes").value(120))
        .andExpect(jsonPath("$.entries.length()").value(2))
        .andExpect(jsonPath("$.entries[0].startDate").value("2026-10-05"))
        .andExpect(jsonPath("$.entries[1].startDate").value("2026-10-11"));
  }

  @Test
  @DisplayName("RG-01-11 : une saisie supprimée n'est ni listée ni comptée")
  void deletedExcluded() throws Exception {
    long id = create(user, hour("2026-10-05", "09:00", "10:00"));
    create(user, hour("2026-10-05", "11:00", "12:00"));
    TimeEntry e = entries.findById(id).orElseThrow();
    e.markDeleted(user.getId(), Instant.now());
    entries.saveAndFlush(e);
    week(user, "2026-10-05")
        .andExpect(jsonPath("$.totalMinutes").value(60))
        .andExpect(jsonPath("$.entries.length()").value(1));
  }

  @Test
  @DisplayName("RG-00-03 : les saisies d'un autre compte sont exclues, le compte vient du jeton")
  void otherAccountExcluded() throws Exception {
    create(other, hour("2026-10-05", "09:00", "10:00"));
    create(manager, hour("2026-10-05", "09:00", "11:00"));
    week(user, "2026-10-05").andExpect(jsonPath("$.entries").isEmpty());
    week(manager, "2026-10-05")
        .andExpect(jsonPath("$.totalMinutes").value(120))
        .andExpect(jsonPath("$.entries.length()").value(1));
  }

  @Test
  @DisplayName(
      "CA-02-07 : un type et un projet désactivés restent renvoyés avec libellé et couleur")
  void inactiveTypeAndProjectStillShown() throws Exception {
    ActivityType t =
        types.saveAndFlush(new ActivityType("OLD_T", "Ancien type", true, TagColor.CONTRAST));
    Project p = projects.saveAndFlush(new Project("OLD_P", "Ancien projet"));
    create(
        user,
        "{\"granularity\":\"DAY\",\"startDate\":\"2026-10-05\",\"activityTypeCode\":\"OLD_T\","
            + "\"projectCode\":\"OLD_P\"}");
    t.setActive(false);
    types.saveAndFlush(t);
    p.setActive(false);
    projects.saveAndFlush(p);
    week(user, "2026-10-05")
        .andExpect(jsonPath("$.entries[0].activityType.code").value("OLD_T"))
        .andExpect(jsonPath("$.entries[0].activityType.label").value("Ancien type"))
        .andExpect(jsonPath("$.entries[0].activityType.color").value("CONTRAST"))
        .andExpect(jsonPath("$.entries[0].activityType.active").value(false))
        .andExpect(jsonPath("$.entries[0].project.name").value("Ancien projet"))
        .andExpect(jsonPath("$.entries[0].project.active").value(false));
  }

  @Test
  @DisplayName(
      "CA-06-04 : une journée standard modifiée après coup ne change pas le total de la semaine")
  void totalUnchangedAfterSettingChange() throws Exception {
    create(user, range("2026-10-05", "2026-10-09"));
    jdbc.update("UPDATE app_settings SET standard_day_minutes = 450");
    week(user, "2026-10-05").andExpect(jsonPath("$.totalMinutes").value(5 * 480));
  }

  @Test
  @DisplayName("RG-01-13 : tri par date de début, puis heure de début")
  void sortedByDateThenTime() throws Exception {
    create(user, hour("2026-10-06", "14:00", "15:00"));
    create(user, hour("2026-10-06", "09:00", "10:00"));
    create(user, hour("2026-10-05", "16:00", "17:00"));
    week(user, "2026-10-05")
        .andExpect(jsonPath("$.entries[0].startDate").value("2026-10-05"))
        .andExpect(jsonPath("$.entries[1].startTime").value("09:00"))
        .andExpect(jsonPath("$.entries[2].startTime").value("14:00"));
  }

  @Test
  @DisplayName(
      "RG-01-13 : weekStart absent, mal formé ou qui n'est pas un lundi donne 400 sur weekStart")
  void invalidWeekStart() throws Exception {
    week(user, "2026-10-06")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("weekStart"));
    week(user, "2026-10-11").andExpect(status().isBadRequest());
    week(user, "pas-une-date")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("weekStart"));
    mockMvc
        .perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("weekStart"));
  }

  @Test
  @DisplayName("RG-01-14 : sans jeton 401, un ADMIN reçoit 403 RG-03-09")
  void accessControl() throws Exception {
    mockMvc.perform(get(URL).param("weekStart", "2026-10-05")).andExpect(status().isUnauthorized());
    week(admin, "2026-10-05")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-03-09"));
    assertThat(entries.count()).isZero();
  }

  private void standardDay(int minutes) {
    jdbc.update("UPDATE app_settings SET standard_day_minutes = ?", minutes);
  }

  /** Vérifie les sept jours du lundi 05/10/2026 : 7 entrées, dates consécutives, minutes. */
  private void expectDays(ResultActions r, int... minutes) throws Exception {
    r.andExpect(jsonPath("$.days.length()").value(7));
    for (int i = 0; i < 7; i++) {
      r.andExpect(
              jsonPath("$.days[" + i + "].date").value("2026-10-" + String.format("%02d", 5 + i)))
          .andExpect(jsonPath("$.days[" + i + "].minutes").value(minutes[i]));
    }
  }

  @Test
  @DisplayName("CA-07-01 : deux saisies HEURE le lundi (90 + 120 min) donnent 210 min (3,5 h)")
  void dailyTotalOfSeveralHourEntries() throws Exception {
    create(user, hour("2026-10-05", "09:00", "10:30"));
    create(user, hour("2026-10-05", "14:00", "16:00"));
    expectDays(week(user, "2026-10-05").andExpect(status().isOk()), 210, 0, 0, 0, 0, 0, 0);
  }

  @Test
  @DisplayName(
      "CA-07-02 : une PLAGE jeudi-mardi compte 480 jeudi et vendredi, 0 samedi et dimanche")
  void dailyTotalOfRangeAcrossWeekend() throws Exception {
    standardDay(480);
    create(user, range("2026-10-08", "2026-10-13"));
    expectDays(week(user, "2026-10-05"), 0, 0, 0, 480, 480, 0, 0);
  }

  @Test
  @DisplayName("CA-07-03 : une JOURNEE le samedi compte 480 min ce jour-là")
  void dailyTotalOfDayOnSaturday() throws Exception {
    standardDay(480);
    create(user, day("2026-10-10"));
    expectDays(week(user, "2026-10-05"), 0, 0, 0, 0, 0, 480, 0);
  }

  @Test
  @DisplayName("CA-07-05 : 90 min le lundi et une JOURNEE le mardi, total de la semaine 570 min")
  void dailyTotalsOfMixedWeek() throws Exception {
    standardDay(480);
    create(user, hour("2026-10-05", "09:00", "10:30"));
    create(user, day("2026-10-06"));
    expectDays(week(user, "2026-10-05"), 90, 480, 0, 0, 0, 0, 0);
  }

  @Test
  @DisplayName(
      "CA-07-06 : une journée standard modifiée après coup ne change pas le total du mardi")
  void dailyTotalUnchangedAfterSettingChange() throws Exception {
    standardDay(480);
    create(user, day("2026-10-06"));
    standardDay(450);
    expectDays(week(user, "2026-10-05"), 0, 480, 0, 0, 0, 0, 0);
  }

  @Test
  @DisplayName("CA-07-08 : une saisie de 09:00 à 09:15 donne 15 min (0,25 h) le lundi")
  void dailyTotalOfQuarterHour() throws Exception {
    create(user, hour("2026-10-05", "09:00", "09:15"));
    expectDays(week(user, "2026-10-05"), 15, 0, 0, 0, 0, 0, 0);
  }

  @Test
  @DisplayName("RG-07-03 : une saisie supprimée n'est pas comptée dans le jour")
  void deletedEntryNotInDailyTotal() throws Exception {
    long id = create(user, hour("2026-10-05", "09:00", "10:30"));
    create(user, hour("2026-10-05", "14:00", "16:00"));
    TimeEntry e = entries.findById(id).orElseThrow();
    e.markDeleted(user.getId(), Instant.now());
    entries.saveAndFlush(e);
    expectDays(week(user, "2026-10-05"), 120, 0, 0, 0, 0, 0, 0);
  }

  @Test
  @DisplayName("RG-07-04 : la somme des sept jours est égale au total de la semaine")
  void daysSumEqualsTotal() throws Exception {
    standardDay(480);
    create(user, hour("2026-10-05", "09:00", "10:30"));
    create(user, range("2026-10-08", "2026-10-13"));
    create(user, day("2026-10-06"));
    String json =
        week(user, "2026-10-05")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    com.fasterxml.jackson.databind.JsonNode root =
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
    int sum = 0;
    for (com.fasterxml.jackson.databind.JsonNode d : root.get("days")) {
      sum += d.get("minutes").asInt();
    }
    assertThat(root.get("totalMinutes").asInt()).isEqualTo(90 + 960 + 480);
    assertThat(sum).isEqualTo(root.get("totalMinutes").asInt());
  }
}
