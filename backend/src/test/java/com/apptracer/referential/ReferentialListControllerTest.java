package com.apptracer.referential;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ReferentialListControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private ActivityTypeRepository types;
  @Autowired private ProjectRepository projects;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User admin;
  private User plainUser;
  private User manager;

  @BeforeEach
  void setUp() {
    admin = users.save(new User("ref-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    plainUser = users.save(new User("ref-user@example.com", "U", "User", "hash", Role.USER));
    manager = users.save(new User("ref-mgr@example.com", "M", "Mgr", "hash", Role.MANAGER));
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM projects");
    jdbc.execute(
        "DELETE FROM activity_types WHERE code NOT IN"
            + " ('TRAVAIL_PROJET','REUNION','LECTURE_SFD','REVUE_MR','FORMATION','AUTRE')");
    jdbc.execute("UPDATE activity_types SET active = TRUE");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  private String bearer(User user) {
    return "Bearer " + tokens.issue(user.getId(), user.getRole(), UUID.randomUUID());
  }

  @Test
  @DisplayName("CA-02-01 : un utilisateur connecté obtient les six types initiaux, actifs")
  void listsSixInitialTypes() throws Exception {
    for (User u : new User[] {plainUser, manager, admin}) {
      mockMvc
          .perform(get("/api/v1/activity-types").header(HttpHeaders.AUTHORIZATION, bearer(u)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(6))
          .andExpect(jsonPath("$[?(@.active == false)]").isEmpty());
    }
  }

  @Test
  @DisplayName(
      "CA-02-02 : projectRequired vrai pour TRAVAIL_PROJET et REVUE_MR, faux pour les autres")
  void projectRequiredFlags() throws Exception {
    mockMvc
        .perform(get("/api/v1/activity-types").header(HttpHeaders.AUTHORIZATION, bearer(plainUser)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.projectRequired == true)].code")
                .value(org.hamcrest.Matchers.containsInAnyOrder("TRAVAIL_PROJET", "REVUE_MR")))
        .andExpect(
            jsonPath("$[?(@.projectRequired == false)].code")
                .value(
                    org.hamcrest.Matchers.containsInAnyOrder(
                        "REUNION", "LECTURE_SFD", "FORMATION", "AUTRE")));
  }

  @Test
  @DisplayName("CA-02-11 : sans jeton, les deux listes répondent 401")
  void unauthenticatedIs401() throws Exception {
    mockMvc.perform(get("/api/v1/activity-types")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "RG-02-09 : USER et MANAGER ne voient pas les inactifs, même en le demandant ; ADMIN les voit")
  void inactiveVisibleToAdminOnly() throws Exception {
    ActivityType off =
        types.saveAndFlush(new ActivityType("OLD_T", "Ancien", false, TagColor.INFO));
    off.setActive(false);
    types.saveAndFlush(off);
    Project p = projects.saveAndFlush(new Project("OLD_P", "Ancien projet"));
    p.setActive(false);
    projects.saveAndFlush(p);
    projects.saveAndFlush(new Project("NEW_P", "Nouveau projet"));

    for (User u : new User[] {plainUser, manager}) {
      mockMvc
          .perform(
              get("/api/v1/activity-types?includeInactive=true&active=false")
                  .header(HttpHeaders.AUTHORIZATION, bearer(u)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(6))
          .andExpect(jsonPath("$[?(@.code == 'OLD_T')]").isEmpty());
      mockMvc
          .perform(
              get("/api/v1/projects?includeInactive=true")
                  .header(HttpHeaders.AUTHORIZATION, bearer(u)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].code").value("NEW_P"));
    }
    mockMvc
        .perform(get("/api/v1/activity-types").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(7))
        .andExpect(jsonPath("$[?(@.code == 'OLD_T')].active").value(false));
    mockMvc
        .perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  @DisplayName(
      "RG-02-10 : types triés par libellé et projets par nom, sans tenir compte de la casse")
  void sortedAlphabetically() throws Exception {
    projects.saveAndFlush(new Project("P3", "zèbre"));
    projects.saveAndFlush(new Project("P1", "alpha"));
    projects.saveAndFlush(new Project("P2", "Bravo"));

    mockMvc
        .perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, bearer(plainUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("alpha"))
        .andExpect(jsonPath("$[1].name").value("Bravo"))
        .andExpect(jsonPath("$[2].name").value("zèbre"));
    mockMvc
        .perform(get("/api/v1/activity-types").header(HttpHeaders.AUTHORIZATION, bearer(plainUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").value("Autre"))
        .andExpect(jsonPath("$[1].label").value("Formation"))
        .andExpect(jsonPath("$[2].label").value("Lecture de SFD"))
        .andExpect(jsonPath("$[3].label").value("Réunion"))
        .andExpect(jsonPath("$[4].label").value("Revue de MR"))
        .andExpect(jsonPath("$[5].label").value("Travail projet"));
  }
}
