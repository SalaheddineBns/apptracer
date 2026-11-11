package com.apptracer.team;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Instant;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TeamUpdateControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private TeamRepository teams;
  @Autowired private TeamMemberRepository members;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User admin;
  private User plainUser;
  private User manager;
  private User newManager;
  private User alice;
  private User bob;
  private User carol;
  private User otherAdmin;
  private Team produit;

  @BeforeEach
  void setUp() {
    admin = users.save(new User("tu-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    otherAdmin = users.save(new User("tu-admin2@example.com", "B", "Admin", "hash", Role.ADMIN));
    plainUser = users.save(new User("tu-user@example.com", "Uma", "Zulu", "hash", Role.USER));
    manager = users.save(new User("tu-mgr@example.com", "Mia", "Manager", "hash", Role.MANAGER));
    newManager =
        users.save(new User("tu-mgr2@example.com", "Max", "Nouveau", "hash", Role.MANAGER));
    alice = users.save(new User("tu-alice@example.com", "Alice", "Aa", "hash", Role.USER));
    bob = users.save(new User("tu-bob@example.com", "Bob", "Bb", "hash", Role.USER));
    carol = users.save(new User("tu-carol@example.com", "Carol", "Cc", "hash", Role.USER));
    produit = team("Produit", manager, alice, bob);
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    jdbc.execute("DELETE FROM team_members");
    jdbc.execute("DELETE FROM teams");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users WHERE email LIKE 'tu-%'");
  }

  /** Starting data attributed to an ADMIN other than the caller, to test RG-05-14. */
  private Team team(String name, User teamManager, User... teamMembers) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                String.valueOf(otherAdmin.getId()), null, java.util.List.of()));
    try {
      Team t = teams.saveAndFlush(new Team(name, teamManager));
      for (User m : teamMembers) {
        members.saveAndFlush(new TeamMember(m.getId(), t.getId()));
      }
      return t;
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, User u, String body) {
    b.header(
        HttpHeaders.AUTHORIZATION,
        "Bearer " + tokens.issue(u.getId(), u.getRole(), UUID.randomUUID()));
    return body == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static String body(String name, Long managerId, long version, Long... memberIds) {
    String ids =
        java.util.Arrays.stream(memberIds)
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(","));
    return "{\"name\":\"%s\",\"managerId\":%d,\"memberIds\":[%s],\"version\":%d}"
        .formatted(name, managerId, ids, version);
  }

  private String url() {
    return "/api/v1/teams/" + produit.getId();
  }

  @Test
  @DisplayName("RG-05-08 : l'ADMIN remplace nom, manager et membres d'un bloc")
  void adminReplacesEverything() throws Exception {
    mockMvc
        .perform(
            as(
                put(url()),
                admin,
                body("Produit 2", newManager.getId(), 0, bob.getId(), carol.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Produit 2"))
        .andExpect(jsonPath("$.manager.id").value(newManager.getId()))
        .andExpect(jsonPath("$.memberCount").value(2))
        .andExpect(jsonPath("$.members[0].id").value(bob.getId()))
        .andExpect(jsonPath("$.members[1].id").value(carol.getId()))
        .andExpect(jsonPath("$.version").value(1));

    // alice est retirée, bob garde sa ligne d'origine, carol en reçoit une
    assertThat(members.findByUserId(alice.getId())).isEmpty();
    assertThat(members.findByUserId(bob.getId()).orElseThrow().getTeamId())
        .isEqualTo(produit.getId());
    assertThat(members.findByUserId(carol.getId()).orElseThrow().getCreatedBy())
        .isEqualTo(admin.getId());
  }

  @Test
  @DisplayName("RG-05-04 : un membre conservé garde la trace de son rattachement d'origine")
  void keptMemberKeepsItsRow() throws Exception {
    Instant before = members.findByUserId(alice.getId()).orElseThrow().getCreatedAt();

    mockMvc
        .perform(
            as(
                put(url()),
                admin,
                body("Produit", manager.getId(), 0, alice.getId(), carol.getId())))
        .andExpect(status().isOk());

    TeamMember kept = members.findByUserId(alice.getId()).orElseThrow();
    assertThat(kept.getCreatedAt()).isEqualTo(before);
    assertThat(kept.getCreatedBy()).isEqualTo(otherAdmin.getId());
  }

  @Test
  @DisplayName("RG-05-14 : un changement de membres seul trace l'ADMIN et incrémente la version")
  void membershipChangeIsTracedOnTheTeam() throws Exception {
    assertThat(teams.findActiveById(produit.getId()).orElseThrow().getUpdatedBy())
        .isEqualTo(otherAdmin.getId());

    mockMvc
        .perform(
            as(
                put(url()),
                admin,
                body("Produit", manager.getId(), 0, alice.getId(), bob.getId(), carol.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));

    Team after = teams.findActiveById(produit.getId()).orElseThrow();
    assertThat(after.getUpdatedBy()).isEqualTo(admin.getId());
    assertThat(after.getUpdatedAt()).isAfter(produit.getCreatedAt());
    assertThat(after.getVersion()).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-05-12 : changer le manager déplace l'équipe de l'ancien vers le nouveau manager")
  void managerChangeMovesTheScope() throws Exception {
    mockMvc
        .perform(
            as(
                put(url()),
                admin,
                body("Produit", newManager.getId(), 0, alice.getId(), bob.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.manager.id").value(newManager.getId()));

    // la consultation elle-même relève de SFD-04 (T-04-3, T-04-9) : ici, la donnée sur laquelle
    // le périmètre du MANAGER est calculé
    assertThat(teams.findActiveNamesByManagerId(manager.getId())).isEmpty();
    assertThat(teams.findActiveNamesByManagerId(newManager.getId())).containsExactly("Produit");
  }

  @Test
  @DisplayName("CA-05-02 : renommer en « PRODUIT » une autre équipe donne 409 RG-05-01 sur name")
  void renameToExistingNameIs409() throws Exception {
    Team technique = team("Technique", manager);

    mockMvc
        .perform(
            as(
                put("/api/v1/teams/" + technique.getId()),
                admin,
                body("PRODUIT", manager.getId(), 0)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-01"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  @DisplayName("RG-05-01 : une équipe peut changer la casse de son propre nom")
  void ownNameIsNotAConflict() throws Exception {
    mockMvc
        .perform(
            as(put(url()), admin, body("PRODUIT", manager.getId(), 0, alice.getId(), bob.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("PRODUIT"));
  }

  @Test
  @DisplayName("CA-05-04 : ajouter le membre d'une autre équipe donne 409 RG-05-04 (équipe nommée)")
  void memberOfAnotherTeamIs409() throws Exception {
    team("Technique", newManager, carol);

    mockMvc
        .perform(
            as(
                put(url()),
                admin,
                body("Produit", manager.getId(), 0, alice.getId(), bob.getId(), carol.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-04"))
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("Technique")))
        .andExpect(jsonPath("$.errors[0].field").value("memberIds"));
    assertThat(members.findByUserId(carol.getId()).orElseThrow().getTeamId())
        .isNotEqualTo(produit.getId());
  }

  @Test
  @DisplayName("CA-05-06 : le manager de l'équipe ajouté comme membre donne 409 RG-05-05")
  void managerAsMemberIs409() throws Exception {
    mockMvc
        .perform(
            as(
                put(url()),
                admin,
                body("Produit", manager.getId(), 0, alice.getId(), manager.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-05"));
  }

  @Test
  @DisplayName("RG-05-02 / RG-05-03 : manager USER ou membre ADMIN donnent 409")
  void ineligibleAccountsAre409() throws Exception {
    mockMvc
        .perform(as(put(url()), admin, body("Produit", plainUser.getId(), 0, alice.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-02"));
    mockMvc
        .perform(as(put(url()), admin, body("Produit", manager.getId(), 0, otherAdmin.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-03"));
  }

  @Test
  @DisplayName("Modification concurrente : une version périmée donne 409 sans code")
  void staleVersionIs409() throws Exception {
    mockMvc
        .perform(as(put(url()), admin, body("Produit", manager.getId(), 5, alice.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(members.findByUserId(bob.getId())).isPresent();
  }

  @Test
  @DisplayName("RG-05-01 : un nom invalide ou un corps incomplet donne 400")
  void invalidBodyIs400() throws Exception {
    mockMvc
        .perform(as(put(url()), admin, body("A", manager.getId(), 0)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("name"));
    mockMvc
        .perform(
            as(
                put(url()),
                admin,
                "{\"name\":\"Produit\",\"managerId\":%d,\"memberIds\":[]}"
                    .formatted(manager.getId())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("version"));
  }

  @Test
  @DisplayName("RG-05-09 : modifier une équipe inconnue donne 404")
  void unknownTeamIs404() throws Exception {
    mockMvc
        .perform(as(put("/api/v1/teams/999999"), admin, body("Produit", manager.getId(), 0)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-05-11 : updateTeam répond 401 sans token")
  void unauthenticatedIs401() throws Exception {
    mockMvc
        .perform(
            put(url())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Produit", manager.getId(), 0)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CA-05-11 : updateTeam répond 403 à un USER et à un MANAGER, sans rien modifier")
  void nonAdminIs403() throws Exception {
    for (User caller : new User[] {plainUser, manager}) {
      mockMvc
          .perform(as(put(url()), caller, body("Autre", manager.getId(), 0)))
          .andExpect(status().isForbidden());
    }
    assertThat(teams.findActiveById(produit.getId()).orElseThrow().getName()).isEqualTo("Produit");
  }
}
