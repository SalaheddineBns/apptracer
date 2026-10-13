package com.apptracer.timeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.domain.TagColor;
import com.apptracer.referential.repository.ActivityTypeRepository;
import com.apptracer.referential.service.ActivityTypeUsageChecker;
import com.apptracer.security.AccessTokenService;
import com.apptracer.timeentry.service.TimeEntryActivityTypeUsageChecker;
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

/** RG-02-07 : « projet requis » d'un type figé dès qu'une saisie l'utilise. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TimeEntryActivityTypeUsageTest {

  private static final String ENTRIES_URL = "/api/v1/time-entries";
  private static final String TYPE_CODE = "ATELIER_USAGE";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private ActivityTypeRepository types;
  @Autowired private ActivityTypeUsageChecker usageChecker;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User user;
  private User admin;
  private ActivityType type;

  @BeforeEach
  void setUp() {
    user = users.save(new User("us-user@example.com", "U", "User", "hash", Role.USER));
    admin = users.save(new User("us-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    type = types.saveAndFlush(new ActivityType(TYPE_CODE, "Atelier", false, TagColor.INFO));
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM time_entries");
    jdbc.update("DELETE FROM activity_types WHERE code = ?", TYPE_CODE);
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  private String bearer(User u) {
    return "Bearer " + tokens.issue(u.getId(), u.getRole(), UUID.randomUUID());
  }

  private long createEntry() throws Exception {
    mockMvc
        .perform(
            post(ENTRIES_URL)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"granularity\":\"HOUR\",\"startDate\":\"2026-10-05\",\"startTime\":\"09:00\","
                        + "\"endTime\":\"10:00\",\"activityTypeCode\":\""
                        + TYPE_CODE
                        + "\"}"))
        .andExpect(status().isCreated());
    return jdbc.queryForObject("SELECT max(id) FROM time_entries", Long.class);
  }

  private ResultActions updateType(String label, boolean projectRequired) throws Exception {
    long version =
        jdbc.queryForObject(
            "SELECT version FROM activity_types WHERE id = ?", Long.class, type.getId());
    return mockMvc.perform(
        put("/api/v1/activity-types/" + type.getId())
            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"label\":\""
                    + label
                    + "\",\"projectRequired\":"
                    + projectRequired
                    + ",\"color\":\"INFO\",\"version\":"
                    + version
                    + "}"));
  }

  @Test
  @DisplayName("RG-02-07 : le port est implémenté sur time_entries, pas par l'implémentation vide")
  void realCheckerIsWired() {
    assertThat(usageChecker).isInstanceOf(TimeEntryActivityTypeUsageChecker.class);
  }

  @Test
  @DisplayName("RG-02-07 : sans saisie sur le type, « projet requis » reste modifiable")
  void unusedTypeCanChange() throws Exception {
    assertThat(usageChecker.isUsed(type.getId())).isFalse();
    updateType("Atelier", true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectRequired").value(true));
  }

  @Test
  @DisplayName(
      "CA-02-10 : un type utilisé par une saisie refuse le changement de « projet requis » (409"
          + " RG-02-07)")
  void usedTypeRefusesChange() throws Exception {
    createEntry();
    assertThat(usageChecker.isUsed(type.getId())).isTrue();
    updateType("Atelier", true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-02-07"))
        .andExpect(jsonPath("$.errors[0].field").value("projectRequired"));
    assertThat(
            jdbc.queryForObject(
                "SELECT project_required FROM activity_types WHERE id = ?",
                Boolean.class,
                type.getId()))
        .isFalse();
  }

  @Test
  @DisplayName("RG-02-07 : les autres champs d'un type utilisé restent modifiables")
  void usedTypeKeepsOtherFieldsEditable() throws Exception {
    createEntry();
    updateType("Atelier renommé", false)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.label").value("Atelier renommé"));
  }

  @Test
  @DisplayName(
      "CA-02-10 : RG-02-07, une saisie supprimée logiquement compte encore comme une utilisation")
  void deletedEntryStillCounts() throws Exception {
    long id = createEntry();
    mockMvc
        .perform(delete(ENTRIES_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNoContent());
    assertThat(usageChecker.isUsed(type.getId())).isTrue();
    updateType("Atelier", true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-02-07"));
  }
}
