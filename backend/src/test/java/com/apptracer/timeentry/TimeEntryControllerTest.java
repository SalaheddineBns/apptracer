package com.apptracer.timeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TimeEntryControllerTest {

  private static final String URL = "/api/v1/time-entries";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private ActivityTypeRepository types;
  @Autowired private ProjectRepository projects;
  @Autowired private TimeEntryRepository entries;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Clock clock;

  private User user;
  private User manager;
  private User admin;
  private User other;

  @BeforeEach
  void setUp() {
    user = users.save(new User("te-user@example.com", "U", "User", "hash", Role.USER));
    manager = users.save(new User("te-mgr@example.com", "M", "Mgr", "hash", Role.MANAGER));
    admin = users.save(new User("te-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    other = users.save(new User("te-other@example.com", "O", "Other", "hash", Role.USER));
  }

  @AfterEach
  void cleanUp() {
    // time entries before accounts; test types and projects before accounts; installation restored
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

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, User u, String body) {
    b.header(
        HttpHeaders.AUTHORIZATION,
        "Bearer " + tokens.issue(u.getId(), u.getRole(), UUID.randomUUID()));
    if (body != null) {
      b.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return b;
  }

  private ResultActions create(User u, String body) throws Exception {
    return mockMvc.perform(as(post(URL), u, body));
  }

  private static String json(String... pairs) {
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < pairs.length; i += 2) {
      if (i > 0) sb.append(',');
      sb.append('"').append(pairs[i]).append("\":").append(pairs[i + 1]);
    }
    return sb.append('}').toString();
  }

  private static String s(String v) {
    return "\"" + v + "\"";
  }

  private String hourBody(String from, String to) {
    return json(
        "granularity", s("HOUR"),
        "startDate", s("2026-10-05"),
        "startTime", s(from),
        "endTime", s(to),
        "activityTypeCode", s("REUNION"));
  }

  private String dayBody(String date) {
    return json("granularity", s("DAY"), "startDate", s(date), "activityTypeCode", s("REUNION"));
  }

  private String rangeBody(String from, String to, String type, String project) {
    return project == null
        ? json(
            "granularity",
            s("RANGE"),
            "startDate",
            s(from),
            "endDate",
            s(to),
            "activityTypeCode",
            s(type))
        : json(
            "granularity",
            s("RANGE"),
            "startDate",
            s(from),
            "endDate",
            s(to),
            "activityTypeCode",
            s(type),
            "projectCode",
            s(project));
  }

  @Test
  @DisplayName("CA-01-01 : un USER saisit 09:00-10:30 en REUNION le 05/10/2026, 201 et 90 min")
  void createsHourEntry() throws Exception {
    create(user, hourBody("09:00", "10:30"))
        .andExpect(status().isCreated())
        .andExpect(
            header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString(URL + "/")))
        .andExpect(jsonPath("$.granularity").value("HOUR"))
        .andExpect(jsonPath("$.startDate").value("2026-10-05"))
        .andExpect(jsonPath("$.startTime").value("09:00"))
        .andExpect(jsonPath("$.endTime").value("10:30"))
        .andExpect(jsonPath("$.durationMinutes").value(90))
        .andExpect(jsonPath("$.activityType.code").value("REUNION"))
        .andExpect(jsonPath("$.activityType.active").value(true))
        .andExpect(jsonPath("$.project").doesNotExist())
        .andExpect(jsonPath("$.endDate").doesNotExist());
    // RG-01-11, RG-00-03: owner = the token's user, traceability
    TimeEntry saved = entries.findAll().get(0);
    assertThat(saved.getUserId()).isEqualTo(user.getId());
    assertThat(saved.getCreatedBy()).isEqualTo(user.getId());
    assertThat(saved.getStandardDayMinutes()).isEqualTo(480);
  }

  @Test
  @DisplayName("CA-01-01 : la saisie créée est relisible par son propriétaire (getMyTimeEntry)")
  void createdEntryIsReadable() throws Exception {
    create(user, hourBody("09:00", "10:30")).andExpect(status().isCreated());
    Long id = entries.findAll().get(0).getId();
    mockMvc
        .perform(as(get(URL + "/" + id), user, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.durationMinutes").value(90))
        .andExpect(jsonPath("$.activityType.label").value("Réunion"));
  }

  @Test
  @DisplayName("RG-01-14 : un MANAGER saisit aussi (201)")
  void managerCreates() throws Exception {
    create(manager, hourBody("09:00", "10:00")).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("CA-01-02 : fin antérieure ou égale au début donne 409 RG-01-01 sur endTime")
  void endBeforeStart() throws Exception {
    for (String end : List.of("08:00", "09:30")) {
      create(user, hourBody("09:30", end))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("RG-01-01"))
          .andExpect(jsonPath("$.errors[0].field").value("endTime"));
    }
    assertThat(entries.count()).isZero();
  }

  @Test
  @DisplayName("RG-01-01 : heures hors pas de 15 minutes donnent 409 RG-01-01")
  void notQuarter() throws Exception {
    create(user, hourBody("09:10", "10:00"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-01-01"))
        .andExpect(jsonPath("$.errors[0].field").value("startTime"));
  }

  @Test
  @DisplayName("RG-01-01 : HOUR sans heure de fin, ou avec endDate, donne 400 par champ")
  void hourShapeIs400() throws Exception {
    create(
            user,
            json(
                "granularity",
                s("HOUR"),
                "startDate",
                s("2026-10-05"),
                "startTime",
                s("09:00"),
                "activityTypeCode",
                s("REUNION")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("endTime"));
    create(
            user,
            json(
                "granularity",
                s("HOUR"),
                "startDate",
                s("2026-10-05"),
                "startTime",
                s("09:00"),
                "endTime",
                s("10:00"),
                "endDate",
                s("2026-10-05"),
                "activityTypeCode",
                s("REUNION")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("endDate"));
  }

  @Test
  @DisplayName("RG-01-02 : JOURNEE sans heures, avec heures refusée en 400")
  void dayShape() throws Exception {
    create(user, dayBody("2026-10-10")) // samedi
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.durationMinutes").value(480))
        .andExpect(jsonPath("$.startTime").doesNotExist());
    create(
            user,
            json(
                "granularity",
                s("DAY"),
                "startDate",
                s("2026-10-06"),
                "startTime",
                s("09:00"),
                "activityTypeCode",
                s("REUNION")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("startTime"));
  }

  @Test
  @DisplayName("CA-01-03 : RG-01-03, PLAGE FORMATION du 05/10 au 16/10/2026 = 4800 min")
  void rangeCreated() throws Exception {
    create(user, rangeBody("2026-10-05", "2026-10-16", "FORMATION", null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.durationMinutes").value(4800))
        .andExpect(jsonPath("$.endDate").value("2026-10-16"));
  }

  @Test
  @DisplayName("RG-01-03 : PLAGE de 31 jours acceptée, de 32 jours, d'un jour ou sans fin refusées")
  void rangeBounds() throws Exception {
    create(user, rangeBody("2026-08-01", "2026-08-31", "FORMATION", null))
        .andExpect(status().isCreated());
    create(user, rangeBody("2026-09-01", "2026-10-02", "FORMATION", null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-01-03"))
        .andExpect(jsonPath("$.errors[0].field").value("endDate"));
    create(user, rangeBody("2026-09-01", "2026-09-01", "FORMATION", null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-01-03"));
    create(
            user,
            json(
                "granularity",
                s("RANGE"),
                "startDate",
                s("2026-09-01"),
                "activityTypeCode",
                s("FORMATION")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("endDate"));
  }

  @Test
  @DisplayName("CA-01-05 : TRAVAIL_PROJET sans projet donne 409 RG-01-05 sur projectCode")
  void projectRequired() throws Exception {
    create(user, rangeBody("2026-10-05", "2026-10-06", "TRAVAIL_PROJET", null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-01-05"))
        .andExpect(jsonPath("$.errors[0].field").value("projectCode"));
    projects.saveAndFlush(new Project("P1", "Projet un"));
    create(user, rangeBody("2026-10-05", "2026-10-06", "TRAVAIL_PROJET", "P1"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.project.code").value("P1"))
        .andExpect(jsonPath("$.project.name").value("Projet un"))
        .andExpect(jsonPath("$.project.active").value(true));
  }

  @Test
  @DisplayName("RG-01-05 : un projet sur un type qui n'en veut pas donne 409 RG-01-05")
  void projectForbidden() throws Exception {
    projects.saveAndFlush(new Project("P1", "Projet un"));
    create(user, rangeBody("2026-10-05", "2026-10-06", "REUNION", "P1"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-01-05"));
  }

  @Test
  @DisplayName("RG-02-06 : un projet inactif donne 409 RG-02-06")
  void inactiveProject() throws Exception {
    Project p = new Project("OFF", "Inactif");
    p.setActive(false);
    projects.saveAndFlush(p);
    create(user, rangeBody("2026-10-05", "2026-10-06", "TRAVAIL_PROJET", "OFF"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-02-06"))
        .andExpect(jsonPath("$.errors[0].field").value("projectCode"));
  }

  @Test
  @DisplayName("RG-01-04 : un type inactif ou inconnu donne 409 RG-01-04")
  void inactiveType() throws Exception {
    ActivityType off = new ActivityType("OLD_TYPE", "Ancien", false, TagColor.INFO);
    off.setActive(false);
    types.saveAndFlush(off);
    for (String code : List.of("OLD_TYPE", "NOPE")) {
      create(
              user,
              json(
                  "granularity",
                  s("DAY"),
                  "startDate",
                  s("2026-10-05"),
                  "activityTypeCode",
                  s(code)))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("RG-01-04"))
          .andExpect(jsonPath("$.errors[0].field").value("activityTypeCode"));
    }
  }

  @Test
  @DisplayName("CA-01-07 : une date de début dans le futur est refusée, aujourd'hui acceptée")
  void futureStartDate() throws Exception {
    LocalDate today = LocalDate.now(clock);
    create(user, dayBody(today.plusDays(1).toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-01-07"))
        .andExpect(jsonPath("$.errors[0].field").value("startDate"));
    create(user, dayBody(today.toString())).andExpect(status().isCreated());
    // RG-01-07: only the start date counts; a range may end in the future
    create(
            user,
            rangeBody(
                today.minusDays(40).toString(), today.minusDays(10).toString(), "FORMATION", null))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("RG-01-09 : commentaire de 500 caractères accepté, de 501 refusé en 400")
  void commentLength() throws Exception {
    create(
            user,
            json(
                "granularity",
                s("DAY"),
                "startDate",
                s("2026-10-05"),
                "activityTypeCode",
                s("REUNION"),
                "comment",
                s("x".repeat(500))))
        .andExpect(status().isCreated());
    create(
            user,
            json(
                "granularity",
                s("DAY"),
                "startDate",
                s("2026-10-06"),
                "activityTypeCode",
                s("REUNION"),
                "comment",
                s("x".repeat(501))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("comment"));
  }

  @Test
  @DisplayName("RG-01-08 : champs obligatoires absents et heure mal formée donnent 400")
  void requiredFields() throws Exception {
    create(user, "{}").andExpect(status().isBadRequest());
    create(user, hourBody("9h", "10:00")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("CA-06-03 : avec une journée standard de 450 min, une JOURNEE dure 450 min")
  void standardDayApplied() throws Exception {
    jdbc.update("UPDATE app_settings SET standard_day_minutes = 450");
    create(user, dayBody("2026-10-05"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.durationMinutes").value(450));
    assertThat(entries.findAll().get(0).getStandardDayMinutes()).isEqualTo(450);
  }

  @Test
  @DisplayName("CA-06-04 : passer la journée standard à 450 ne change pas une saisie à 480 min")
  void existingEntryKeepsItsDuration() throws Exception {
    create(user, dayBody("2026-10-05"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.durationMinutes").value(480));
    Long id = entries.findAll().get(0).getId();
    mockMvc
        .perform(
            as(
                put("/api/v1/app-settings"),
                admin,
                json("standardDayMinutes", "450", "version", "0")))
        .andExpect(status().isOk());
    mockMvc
        .perform(as(get(URL + "/" + id), user, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.durationMinutes").value(480));
    assertThat(entries.findById(id).orElseThrow().getStandardDayMinutes()).isEqualTo(480);
  }

  @Test
  @DisplayName("CA-01-08 : RG-01-10, lire la saisie d'un autre USER donne 403 RG-01-10")
  void otherUsersEntry() throws Exception {
    create(other, dayBody("2026-10-05")).andExpect(status().isCreated());
    Long id = entries.findAll().get(0).getId();
    mockMvc
        .perform(as(get(URL + "/" + id), user, null))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-01-10"));
    // a MANAGER cannot reach someone else's time entry through this endpoint either
    mockMvc
        .perform(as(get(URL + "/" + id), manager, null))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-01-10"));
  }

  @Test
  @DisplayName("RG-01-11 : une saisie inconnue ou supprimée logiquement donne 404")
  void unknownOrDeleted() throws Exception {
    mockMvc.perform(as(get(URL + "/999999"), user, null)).andExpect(status().isNotFound());
    create(user, dayBody("2026-10-05")).andExpect(status().isCreated());
    TimeEntry e = entries.findAll().get(0);
    e.markDeleted(user.getId(), Instant.now());
    entries.saveAndFlush(e);
    mockMvc.perform(as(get(URL + "/" + e.getId()), user, null)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("RG-01-14 : sans jeton 401, un ADMIN reçoit 403 sur création et lecture")
  void accessControl() throws Exception {
    mockMvc
        .perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(dayBody("2026-10-05")))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get(URL + "/1")).andExpect(status().isUnauthorized());
    create(admin, dayBody("2026-10-05"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-03-09"));
    mockMvc
        .perform(as(get(URL + "/1"), admin, null))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-03-09"));
    assertThat(entries.count()).isZero();
  }

  @Test
  @DisplayName("RG-00-03 : un propriétaire envoyé dans le corps est ignoré")
  void ownerFromTokenOnly() throws Exception {
    create(
            user,
            json(
                "granularity",
                s("DAY"),
                "startDate",
                s("2026-10-05"),
                "activityTypeCode",
                s("REUNION"),
                "userId",
                String.valueOf(other.getId())))
        .andExpect(status().isCreated());
    assertThat(entries.findAll().get(0).getUserId()).isEqualTo(user.getId());
  }
}
