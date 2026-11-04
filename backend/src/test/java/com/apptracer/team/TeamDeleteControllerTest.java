package com.apptracer.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TeamDeleteControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private TeamRepository teams;
  @Autowired private TeamMemberRepository members;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User admin;
  private User plainUser;
  private User manager;
  private User alice;
  private User bob;
  private User carol;
  private Team produit;

  @BeforeEach
  void setUp() {
    admin = users.save(new User("td-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    plainUser = users.save(new User("td-user@example.com", "Uma", "Zulu", "hash", Role.USER));
    manager = users.save(new User("td-mgr@example.com", "Mia", "Manager", "hash", Role.MANAGER));
    alice = users.save(new User("td-alice@example.com", "Alice", "Aa", "hash", Role.USER));
    bob = users.save(new User("td-bob@example.com", "Bob", "Bb", "hash", Role.USER));
    carol = users.save(new User("td-carol@example.com", "Carol", "Cc", "hash", Role.USER));
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                String.valueOf(admin.getId()), null, java.util.List.of()));
    try {
      produit = teams.saveAndFlush(new Team("Produit", manager));
      for (User m : new User[] {alice, bob, carol}) {
        members.saveAndFlush(new TeamMember(m.getId(), produit.getId()));
      }
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute(
        "DELETE FROM time_entries WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'td-%')");
    jdbc.execute("DELETE FROM team_members");
    jdbc.execute("DELETE FROM teams");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users WHERE email LIKE 'td-%'");
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, User u, String body) {
    b.header(
        HttpHeaders.AUTHORIZATION,
        "Bearer " + tokens.issue(u.getId(), u.getRole(), UUID.randomUUID()));
    return body == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private String url() {
    return "/api/v1/teams/" + produit.getId();
  }

  private void insertTimeEntry(User owner) {
    jdbc.update(
        """
        INSERT INTO time_entries (user_id, granularity, start_date, end_date, activity_type_id,
          duration_minutes, standard_day_minutes, created_at, created_by, updated_at, updated_by)
        VALUES (?, 'DAY', DATE '2026-10-12', DATE '2026-10-12',
          (SELECT id FROM activity_types WHERE code = 'AUTRE'), 480, 480,
          now(), ?, now(), ?)
        """,
        owner.getId(),
        owner.getId(),
        owner.getId());
  }

  @Test
  @DisplayName("CA-05-09 : supprimer une équipe de trois membres la retire, les comptes restent")
  void deletingTeamKeepsAccountsAndEntries() throws Exception {
    insertTimeEntry(alice);
    insertTimeEntry(bob);
    long usersBefore = users.count();

    mockMvc.perform(as(delete(url()), admin, null)).andExpect(status().isNoContent());

    mockMvc
        .perform(as(get("/api/v1/teams"), admin, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Produit')]").doesNotExist());
    mockMvc.perform(as(get(url()), admin, null)).andExpect(status().isNotFound());
    // les trois comptes existent toujours, actifs, sans équipe
    assertThat(users.count()).isEqualTo(usersBefore);
    for (User m : new User[] {alice, bob, carol}) {
      assertThat(users.findById(m.getId()).orElseThrow().isActive()).isTrue();
      assertThat(members.findByUserId(m.getId())).isEmpty();
    }
    // leurs saisies ne sont pas modifiées
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM time_entries WHERE user_id IN (?, ?) AND deleted_at IS NULL",
                Integer.class,
                alice.getId(),
                bob.getId()))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("CA-05-09 / RG-05-14 : la ligne est conservée avec qui et quand a supprimé")
  void deletionIsTraced() throws Exception {
    mockMvc.perform(as(delete(url()), admin, null)).andExpect(status().isNoContent());

    Team kept = teams.findById(produit.getId()).orElseThrow();
    assertThat(kept.getDeletedBy()).isEqualTo(admin.getId());
    assertThat(kept.getDeletedAt()).isNotNull();
    assertThat(kept.getUpdatedBy()).isEqualTo(admin.getId());
  }

  @Test
  @DisplayName("RG-05-09 : le nom d'une équipe supprimée est de nouveau disponible")
  void nameIsAvailableAgain() throws Exception {
    mockMvc.perform(as(delete(url()), admin, null)).andExpect(status().isNoContent());

    mockMvc
        .perform(
            as(
                post("/api/v1/teams"),
                admin,
                "{\"name\":\"Produit\",\"managerId\":%d,\"memberIds\":[%d]}"
                    .formatted(manager.getId(), alice.getId())))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("RG-05-09 : une équipe supprimée ne se supprime ni ne se modifie plus (404)")
  void deletedTeamIsGone() throws Exception {
    mockMvc.perform(as(delete(url()), admin, null)).andExpect(status().isNoContent());

    mockMvc.perform(as(delete(url()), admin, null)).andExpect(status().isNotFound());
    mockMvc
        .perform(as(delete("/api/v1/teams/999999"), admin, null))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-05-11 : deleteTeam répond 401 sans token")
  void unauthenticatedIs401() throws Exception {
    mockMvc.perform(delete(url())).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CA-05-11 : deleteTeam répond 403 à un USER et à un MANAGER, l'équipe reste")
  void nonAdminIs403() throws Exception {
    for (User caller : new User[] {plainUser, manager}) {
      mockMvc.perform(as(delete(url()), caller, null)).andExpect(status().isForbidden());
    }
    assertThat(teams.findActiveById(produit.getId())).isPresent();
    assertThat(members.findByUserId(alice.getId())).isPresent();
  }
}
