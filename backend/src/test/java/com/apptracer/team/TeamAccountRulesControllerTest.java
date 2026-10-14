package com.apptracer.team;

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
import com.apptracer.team.domain.Team;
import com.apptracer.team.domain.TeamMember;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import java.util.UUID;
import org.hamcrest.Matchers;
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

/** RG-05-10 et RG-05-12 dans la gestion des comptes (SFD-03), avec des équipes réelles. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TeamAccountRulesControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private TeamRepository teams;
  @Autowired private TeamMemberRepository members;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User admin;
  private User manager;
  private User freeManager;
  private User alice;
  private Team produit;

  @BeforeEach
  void setUp() {
    admin = users.save(new User("ta-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    manager = users.save(new User("ta-mgr@example.com", "Mia", "Manager", "hash", Role.MANAGER));
    freeManager =
        users.save(new User("ta-free@example.com", "Fred", "Libre", "hash", Role.MANAGER));
    alice = users.save(new User("ta-alice@example.com", "Alice", "Aa", "hash", Role.USER));
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                String.valueOf(admin.getId()), null, java.util.List.of()));
    try {
      produit = teams.saveAndFlush(new Team("Produit", manager));
      members.saveAndFlush(new TeamMember(alice.getId(), produit.getId()));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM team_members");
    jdbc.execute("DELETE FROM teams");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users WHERE email LIKE 'ta-%'");
  }

  private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder b, String body) {
    b.header(
        HttpHeaders.AUTHORIZATION,
        "Bearer " + tokens.issue(admin.getId(), admin.getRole(), UUID.randomUUID()));
    return body == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static String roleBody(User user, String role) {
    return "{\"firstName\":\"%s\",\"lastName\":\"%s\",\"role\":\"%s\",\"version\":%d}"
        .formatted(user.getFirstName(), user.getLastName(), role, user.getVersion());
  }

  @Test
  @DisplayName("CA-05-07 : désactiver le manager d'une équipe donne 409 RG-05-10 nommant l'équipe")
  void deactivatingTeamManagerIs409() throws Exception {
    mockMvc
        .perform(asAdmin(post("/api/v1/users/" + manager.getId() + "/deactivation"), null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-10"))
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("« Produit »")))
        .andExpect(jsonPath("$.errors").doesNotExist());

    assertThat(users.findById(manager.getId()).orElseThrow().isActive()).isTrue();
  }

  @Test
  @DisplayName("CA-05-07 : un manager qui n'a plus d'équipe (supprimée) peut être désactivé")
  void managerOfDeletedTeamCanBeDeactivated() throws Exception {
    mockMvc
        .perform(asAdmin(delete("/api/v1/teams/" + produit.getId()), null))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(asAdmin(post("/api/v1/users/" + manager.getId() + "/deactivation"), null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  @DisplayName("CA-05-07 : après le changement de manager, l'ancien peut être désactivé")
  void formerManagerCanBeDeactivated() throws Exception {
    mockMvc
        .perform(
            asAdmin(
                put("/api/v1/teams/" + produit.getId()),
                "{\"name\":\"Produit\",\"managerId\":%d,\"memberIds\":[%d],\"version\":0}"
                    .formatted(freeManager.getId(), alice.getId())))
        .andExpect(status().isOk());

    mockMvc
        .perform(asAdmin(post("/api/v1/users/" + manager.getId() + "/deactivation"), null))
        .andExpect(status().isOk());
    mockMvc
        .perform(asAdmin(post("/api/v1/users/" + freeManager.getId() + "/deactivation"), null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-10"));
  }

  @Test
  @DisplayName("CA-05-08 : retirer le rôle MANAGER (vers USER) au manager d'une équipe donne 409")
  void removingManagerRoleIs409() throws Exception {
    mockMvc
        .perform(asAdmin(put("/api/v1/users/" + manager.getId()), roleBody(manager, "USER")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-10"))
        .andExpect(jsonPath("$.errors[0].field").value("role"));

    assertThat(users.findById(manager.getId()).orElseThrow().getRole()).isEqualTo(Role.MANAGER);
  }

  @Test
  @DisplayName("CA-05-08 : retirer le rôle MANAGER (vers ADMIN) donne aussi 409 RG-05-10")
  void removingManagerRoleToAdminIs409() throws Exception {
    mockMvc
        .perform(asAdmin(put("/api/v1/users/" + manager.getId()), roleBody(manager, "ADMIN")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-10"))
        .andExpect(jsonPath("$.errors[0].field").value("role"));
  }

  @Test
  @DisplayName("CA-05-08 : un manager sans équipe change de rôle librement")
  void managerWithoutTeamChangesRole() throws Exception {
    mockMvc
        .perform(
            asAdmin(put("/api/v1/users/" + freeManager.getId()), roleBody(freeManager, "USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("USER"));
  }

  @Test
  @DisplayName("RG-05-12 : un membre dont le rôle devient ADMIN est retiré, l'équipe est tracée")
  void memberBecomingAdminIsRemovedFromTeam() throws Exception {
    long versionBefore = teams.findActiveById(produit.getId()).orElseThrow().getVersion();

    mockMvc
        .perform(asAdmin(put("/api/v1/users/" + alice.getId()), roleBody(alice, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));

    assertThat(members.findByUserId(alice.getId())).isEmpty();
    Team after = teams.findActiveById(produit.getId()).orElseThrow();
    assertThat(after.getUpdatedBy()).isEqualTo(admin.getId());
    assertThat(after.getVersion()).isEqualTo(versionBefore + 1);
    mockMvc
        .perform(asAdmin(get("/api/v1/teams/" + produit.getId()), null))
        .andExpect(jsonPath("$.memberCount").value(0));
  }

  @Test
  @DisplayName(
      "RG-05-12 : un MANAGER membre d'une équipe, sans équipe à gérer, devenu ADMIN est retiré")
  void memberManagerBecomingAdminIsRemoved() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                String.valueOf(admin.getId()), null, java.util.List.of()));
    try {
      members.saveAndFlush(new TeamMember(freeManager.getId(), produit.getId()));
    } finally {
      SecurityContextHolder.clearContext();
    }

    mockMvc
        .perform(
            asAdmin(put("/api/v1/users/" + freeManager.getId()), roleBody(freeManager, "ADMIN")))
        .andExpect(status().isOk());

    assertThat(members.findByUserId(freeManager.getId())).isEmpty();
    assertThat(members.findByUserId(alice.getId())).isPresent();
  }

  @Test
  @DisplayName("RG-05-12 : un membre qui devient MANAGER reste dans son équipe")
  void memberBecomingManagerStays() throws Exception {
    mockMvc
        .perform(asAdmin(put("/api/v1/users/" + alice.getId()), roleBody(alice, "MANAGER")))
        .andExpect(status().isOk());

    assertThat(members.findByUserId(alice.getId())).isPresent();
  }

  @Test
  @DisplayName("CA-05-10 : désactiver un membre le laisse dans l'équipe, signalé inactif")
  void deactivatedMemberStaysInTeamFlaggedInactive() throws Exception {
    mockMvc
        .perform(asAdmin(post("/api/v1/users/" + alice.getId() + "/deactivation"), null))
        .andExpect(status().isOk());

    assertThat(members.findByUserId(alice.getId())).isPresent();
    mockMvc
        .perform(asAdmin(get("/api/v1/teams/" + produit.getId()), null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberCount").value(1))
        .andExpect(jsonPath("$.members[0].id").value(alice.getId()))
        .andExpect(jsonPath("$.members[0].active").value(false));
    // la version de l'équipe n'a pas bougé : l'équipe n'est pas modifiée
    assertThat(teams.findActiveById(produit.getId()).orElseThrow().getVersion()).isZero();
  }

  @Test
  @DisplayName("RG-05-10 : renommer le manager d'une équipe en gardant MANAGER reste possible")
  void renamingTeamManagerIsAllowed() throws Exception {
    mockMvc
        .perform(asAdmin(put("/api/v1/users/" + manager.getId()), roleBody(manager, "MANAGER")))
        .andExpect(status().isOk());
  }
}
