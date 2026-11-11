package com.apptracer.teamview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.security.AccessTokenService;
import com.apptracer.team.domain.Team;
import com.apptracer.team.domain.TeamMember;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import java.util.Map;
import java.util.Set;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** RG-04-03, RG-01-10: the team view is read-only, including for a member's time entries. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TeamViewReadOnlyTest {

  private static final String ENTRIES = "/api/v1/time-entries";
  private static final String UPDATE_BODY =
      "{\"granularity\":\"DAY\",\"startDate\":\"2026-10-07\",\"activityTypeCode\":\"REUNION\","
          + "\"version\":0}";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private TeamRepository teams;
  @Autowired private TeamMemberRepository members;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RequestMappingHandlerMapping handlerMapping;

  private User admin;
  private User manager;
  private User anne;
  private Team alpha;
  private long anneEntryId;

  @BeforeEach
  void setUp() throws Exception {
    admin = users.save(new User("tv4-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    manager = users.save(new User("tv4-mgr@example.com", "Mia", "Manager", "hash", Role.MANAGER));
    anne = users.save(new User("tv4-anne@example.com", "Anne", "Adam", "hash", Role.USER));
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                String.valueOf(admin.getId()), null, java.util.List.of()));
    try {
      alpha = teams.saveAndFlush(new Team("Alpha", manager));
      members.saveAndFlush(new TeamMember(anne.getId(), alpha.getId()));
    } finally {
      SecurityContextHolder.clearContext();
    }
    mockMvc
        .perform(
            as(post(ENTRIES), anne)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"granularity\":\"DAY\",\"startDate\":\"2026-10-06\","
                        + "\"activityTypeCode\":\"REUNION\"}"))
        .andExpect(status().isCreated());
    anneEntryId = jdbc.queryForObject("SELECT max(id) FROM time_entries", Long.class);
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    jdbc.execute("DELETE FROM time_entries");
    jdbc.execute("DELETE FROM team_members");
    jdbc.execute("DELETE FROM teams");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users WHERE email LIKE 'tv4-%'");
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, User u) {
    return b.header(
        HttpHeaders.AUTHORIZATION,
        "Bearer " + tokens.issue(u.getId(), u.getRole(), UUID.randomUUID()));
  }

  @Test
  @DisplayName("CA-04-04 : un MANAGER qui modifie la saisie d'un membre de son équipe reçoit 403")
  void managerCannotUpdateAMemberEntry() throws Exception {
    mockMvc
        .perform(
            as(put(ENTRIES + "/{id}", anneEntryId), manager)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-01-10"));
    // the member's time entry is intact
    assertThat(
            jdbc.queryForObject(
                "SELECT start_date FROM time_entries WHERE id = ?",
                java.sql.Date.class,
                anneEntryId))
        .hasToString("2026-10-06");
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM time_entries WHERE id = ?", Long.class, anneEntryId))
        .isZero();
  }

  @Test
  @DisplayName("CA-04-04 : un MANAGER qui supprime la saisie d'un membre de son équipe reçoit 403")
  void managerCannotDeleteAMemberEntry() throws Exception {
    mockMvc
        .perform(as(delete(ENTRIES + "/{id}", anneEntryId), manager))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-01-10"));
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NULL FROM time_entries WHERE id = ?",
                Boolean.class,
                anneEntryId))
        .isTrue();
  }

  @Test
  @DisplayName("CA-04-04 : un MANAGER ne peut pas non plus lire la saisie d'un membre par son id")
  void managerCannotReadAMemberEntryById() throws Exception {
    mockMvc
        .perform(as(get(ENTRIES + "/{id}", anneEntryId), manager))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "RG-04-03 : aucune écriture n'est acceptée sous /team-view (405, rien n'est modifié)")
  void writesUnderTeamViewAreRejected() throws Exception {
    String[] paths = {
      "/api/v1/team-view/teams",
      "/api/v1/team-view/teams/" + alpha.getId() + "/week?weekStart=2026-10-05",
      "/api/v1/team-view/members/" + anne.getId() + "/time-entries?weekStart=2026-10-05",
      "/api/v1/team-view/members/" + anne.getId() + "/time-entries/" + anneEntryId,
      "/api/v1/team-view"
    };
    for (String path : paths) {
      for (HttpMethod method :
          new HttpMethod[] {HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
        MockHttpServletRequestBuilder request =
            switch (method.name()) {
              case "POST" -> post(path);
              case "PUT" -> put(path);
              case "PATCH" -> patch(path);
              default -> delete(path);
            };
        int status =
            mockMvc
                .perform(
                    as(request, manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status).as("%s %s", method, path).isIn(404, 405);
      }
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM time_entries", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("RG-04-03 : tous les endpoints déclarés sous /team-view sont des GET")
  void everyTeamViewMappingIsGetOnly() {
    Map<RequestMappingInfo, HandlerMethod> mappings = handlerMapping.getHandlerMethods();
    var teamView =
        mappings.keySet().stream()
            .filter(
                info ->
                    info.getPathPatternsCondition().getPatternValues().stream()
                        .anyMatch(p -> p.startsWith("/api/v1/team-view")))
            .toList();
    assertThat(teamView).hasSize(3);
    for (RequestMappingInfo info : teamView) {
      assertThat(info.getMethodsCondition().getMethods())
          .as("%s", info.getPathPatternsCondition().getPatternValues())
          .isEqualTo(Set.of(org.springframework.web.bind.annotation.RequestMethod.GET));
    }
  }
}
