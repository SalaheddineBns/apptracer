package com.apptracer.teamview;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ManagedTeamsControllerTest {

  private static final String URL = "/api/v1/team-view/teams";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private TeamRepository teams;
  @Autowired private TeamMemberRepository members;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User admin;
  private User plainUser;
  private User manager;
  private User otherManager;
  private User emptyManager;
  private User alice;

  @BeforeEach
  void setUp() {
    admin = users.save(new User("tv1-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    plainUser = users.save(new User("tv1-user@example.com", "U", "User", "hash", Role.USER));
    manager = users.save(new User("tv1-mgr@example.com", "Mia", "Manager", "hash", Role.MANAGER));
    otherManager = users.save(new User("tv1-mgr2@example.com", "O", "Other", "hash", Role.MANAGER));
    emptyManager = users.save(new User("tv1-mgr3@example.com", "E", "Empty", "hash", Role.MANAGER));
    alice = users.save(new User("tv1-alice@example.com", "Alice", "Aa", "hash", Role.USER));
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    jdbc.execute("DELETE FROM team_members");
    jdbc.execute("DELETE FROM teams");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users WHERE email LIKE 'tv1-%'");
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, User u) {
    return b.header(
        HttpHeaders.AUTHORIZATION,
        "Bearer " + tokens.issue(u.getId(), u.getRole(), UUID.randomUUID()));
  }

  private Team team(String name, User teamManager, User... teamMembers) {
    // RG-00-04 : les écritures de mise en place sont attribuées à l'ADMIN
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                String.valueOf(admin.getId()), null, java.util.List.of()));
    try {
      Team team = teams.saveAndFlush(new Team(name, teamManager));
      for (User m : teamMembers) {
        members.saveAndFlush(new TeamMember(m.getId(), team.getId()));
      }
      return team;
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  @DisplayName("CA-04-05 / RG-04-01 : sans jeton, 401")
  void withoutTokenIs401() throws Exception {
    mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CA-04-05 : un USER reçoit 403")
  void userIs403() throws Exception {
    mockMvc.perform(as(get(URL), plainUser)).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("CA-04-06 : un ADMIN reçoit 403")
  void adminIs403() throws Exception {
    mockMvc.perform(as(get(URL), admin)).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("CA-04-09 / RG-04-01 : un MANAGER sans équipe obtient 200 et une liste vide")
  void managerWithoutTeamGetsEmptyList() throws Exception {
    team("Autre", otherManager);
    mockMvc
        .perform(as(get(URL), emptyManager))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName(
      "RG-04-08 / RG-04-02 : ses équipes seulement, triées par nom ; la première est celle par défaut")
  void listsOnlyMyTeamsSortedByName() throws Exception {
    Team zeta = team("Zeta", manager, alice);
    Team beta = team("beta", manager);
    team("Alpha", otherManager);
    mockMvc
        .perform(as(get(URL), manager))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(beta.getId()))
        .andExpect(jsonPath("$[0].name").value("beta"))
        .andExpect(jsonPath("$[1].id").value(zeta.getId()));
  }

  @Test
  @DisplayName("RG-04-06 : une équipe supprimée n'est plus listée")
  void deletedTeamIsNotListed() throws Exception {
    Team gone = team("Gone", manager);
    team("Kept", manager);
    jdbc.update(
        "UPDATE teams SET deleted_at = ?, deleted_by = ? WHERE id = ?",
        java.sql.Timestamp.from(Instant.now()),
        admin.getId(),
        gone.getId());
    mockMvc
        .perform(as(get(URL), manager))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Kept"));
  }
}
