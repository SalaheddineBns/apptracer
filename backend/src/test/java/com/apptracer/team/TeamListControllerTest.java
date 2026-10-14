package com.apptracer.team;

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
class TeamListControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private TeamRepository teams;
  @Autowired private TeamMemberRepository members;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User admin;
  private User plainUser;
  private User manager;
  private User inactiveManager;
  private User alice;
  private User bob;
  private User inactiveMember;

  @BeforeEach
  void setUp() {
    admin = users.save(new User("tl-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    plainUser = users.save(new User("tl-user@example.com", "Uma", "Zulu", "hash", Role.USER));
    manager = users.save(new User("tl-mgr@example.com", "Mia", "Manager", "hash", Role.MANAGER));
    inactiveManager =
        users.save(new User("tl-mgr2@example.com", "Ines", "Inactive", "hash", Role.MANAGER));
    inactiveManager.setActive(false);
    users.save(inactiveManager);
    alice = users.save(new User("tl-alice@example.com", "Alice", "Aa", "hash", Role.USER));
    bob = users.save(new User("tl-bob@example.com", "Bob", "Bb", "hash", Role.USER));
    inactiveMember = users.save(new User("tl-in@example.com", "Ivan", "Cc", "hash", Role.USER));
    inactiveMember.setActive(false);
    users.save(inactiveMember);
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    jdbc.execute("DELETE FROM team_members");
    jdbc.execute("DELETE FROM teams");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users WHERE email LIKE 'tl-%'");
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
  @DisplayName("CA-05-01 / RG-05-13 : la liste affiche manager et nombre de membres, triée par nom")
  void listShowsManagerAndMemberCountSortedByName() throws Exception {
    team("Technique", manager, bob);
    team("produit", manager, alice, inactiveMember);
    mockMvc
        .perform(as(get("/api/v1/teams"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        // RG-05-13 : tri par nom, insensible à la casse
        .andExpect(jsonPath("$[0].name").value("produit"))
        .andExpect(jsonPath("$[1].name").value("Technique"))
        .andExpect(jsonPath("$[0].manager.id").value(manager.getId()))
        .andExpect(jsonPath("$[0].memberCount").value(2))
        .andExpect(jsonPath("$[1].memberCount").value(1))
        // RG-05-13 : membres triés par nom puis prénom (Aa, Cc)
        .andExpect(jsonPath("$[0].members[0].id").value(alice.getId()))
        .andExpect(jsonPath("$[0].members[1].id").value(inactiveMember.getId()));
  }

  @Test
  @DisplayName("CA-05-10 : un membre au compte désactivé reste dans l'équipe, signalé inactif")
  void inactiveMemberStaysAndIsFlagged() throws Exception {
    Team t = team("Produit", manager, alice, inactiveMember);
    mockMvc
        .perform(as(get("/api/v1/teams/" + t.getId()), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberCount").value(2))
        .andExpect(jsonPath("$.members[0].active").value(true))
        .andExpect(jsonPath("$.members[1].active").value(false));
    mockMvc
        .perform(as(get("/api/v1/teams"), admin))
        .andExpect(jsonPath("$[0].members[1].active").value(false));
  }

  @Test
  @DisplayName("RG-05-07 : une liste sans équipe est vide")
  void emptyList() throws Exception {
    mockMvc
        .perform(as(get("/api/v1/teams"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("CA-05-03 : les candidats managers sont les seuls comptes MANAGER actifs")
  void managerCandidatesOnlyActiveManagers() throws Exception {
    mockMvc
        .perform(as(get("/api/v1/teams/manager-candidates"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.email == 'tl-mgr@example.com')]").exists())
        // ni USER, ni ADMIN, ni manager désactivé
        .andExpect(jsonPath("$[?(@.email == 'tl-user@example.com')]").doesNotExist())
        .andExpect(jsonPath("$[?(@.email == 'tl-admin@example.com')]").doesNotExist())
        .andExpect(jsonPath("$[?(@.email == 'tl-mgr2@example.com')]").doesNotExist())
        .andExpect(jsonPath("$[?(@.role != 'MANAGER')]").doesNotExist())
        .andExpect(jsonPath("$[?(@.active == false)]").doesNotExist());
  }

  @Test
  @DisplayName("CA-05-05 : les candidats membres excluent les ADMIN, inactifs et équipes signalés")
  void memberCandidatesExcludeAdminsAndCarryTeam() throws Exception {
    Team produit = team("Produit", manager, alice, inactiveMember);

    mockMvc
        .perform(as(get("/api/v1/teams/member-candidates"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.role == 'ADMIN')]").doesNotExist())
        .andExpect(jsonPath("$[?(@.email == 'tl-admin@example.com')]").doesNotExist())
        // USER et MANAGER, y compris inactifs (H9)
        .andExpect(jsonPath("$[?(@.email == 'tl-user@example.com')]").exists())
        .andExpect(jsonPath("$[?(@.email == 'tl-mgr@example.com')]").exists())
        .andExpect(jsonPath("$[?(@.email == 'tl-in@example.com')].active").value(false))
        // RG-05-04 : équipe actuelle de chaque candidat
        .andExpect(
            jsonPath("$[?(@.email == 'tl-alice@example.com')].currentTeam.name").value("Produit"))
        .andExpect(
            jsonPath("$[?(@.email == 'tl-alice@example.com')].currentTeam.id")
                .value(produit.getId().intValue()))
        .andExpect(
            jsonPath("$[?(@.email == 'tl-bob@example.com' && @.currentTeam == null)]").exists());
  }

  @Test
  @DisplayName("CA-05-11 : listTeams et les deux listes de candidats répondent 401 sans token")
  void unauthenticatedIs401() throws Exception {
    for (String url :
        new String[] {
          "/api/v1/teams", "/api/v1/teams/manager-candidates", "/api/v1/teams/member-candidates"
        }) {
      mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
    }
  }

  @Test
  @DisplayName(
      "CA-05-11 : listTeams et les deux listes de candidats répondent 403 à USER et MANAGER")
  void nonAdminIs403() throws Exception {
    for (User caller : new User[] {plainUser, manager}) {
      for (String url :
          new String[] {
            "/api/v1/teams", "/api/v1/teams/manager-candidates", "/api/v1/teams/member-candidates"
          }) {
        mockMvc.perform(as(get(url), caller)).andExpect(status().isForbidden());
      }
    }
  }
}
