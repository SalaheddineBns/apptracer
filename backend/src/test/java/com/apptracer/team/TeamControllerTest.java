package com.apptracer.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.security.AccessTokenService;
import com.apptracer.team.domain.Team;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TeamControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @MockitoSpyBean private TeamRepository teams;
  @MockitoSpyBean private TeamMemberRepository members;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User admin;
  private User plainUser;
  private User manager;
  private User otherManager;
  private User alice;
  private User bob;
  private User otherAdmin;

  @BeforeEach
  void setUp() {
    admin = users.save(new User("tc-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    otherAdmin = users.save(new User("tc-admin2@example.com", "B", "Admin", "hash", Role.ADMIN));
    plainUser = users.save(new User("tc-user@example.com", "U", "User", "hash", Role.USER));
    manager = users.save(new User("tc-mgr@example.com", "Mia", "Manager", "hash", Role.MANAGER));
    otherManager =
        users.save(new User("tc-mgr2@example.com", "Max", "Manager", "hash", Role.MANAGER));
    alice = users.save(new User("tc-alice@example.com", "Alice", "Aa", "hash", Role.USER));
    bob = users.save(new User("tc-bob@example.com", "Bob", "Bb", "hash", Role.USER));
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM team_members");
    jdbc.execute("DELETE FROM teams");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users WHERE email LIKE 'tc-%'");
  }

  private String bearer(User user) {
    return "Bearer " + tokens.issue(user.getId(), user.getRole(), UUID.randomUUID());
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, User u, String body) {
    b.header(HttpHeaders.AUTHORIZATION, bearer(u));
    if (body != null) {
      b.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return b;
  }

  private static String createBody(String name, Long managerId, Long... memberIds) {
    String ids =
        java.util.Arrays.stream(memberIds)
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(","));
    return "{\"name\":\"%s\",\"managerId\":%d,\"memberIds\":[%s]}".formatted(name, managerId, ids);
  }

  @Test
  @DisplayName(
      "CA-05-01 : l'ADMIN crée « Produit » avec un manager et deux membres (201, Location)")
  void adminCreatesTeam() throws Exception {
    String location =
        mockMvc
            .perform(
                as(
                    post("/api/v1/teams"),
                    admin,
                    createBody("Produit", manager.getId(), bob.getId(), alice.getId())))
            .andExpect(status().isCreated())
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.name").value("Produit"))
            .andExpect(jsonPath("$.manager.id").value(manager.getId()))
            .andExpect(jsonPath("$.manager.role").value("MANAGER"))
            .andExpect(jsonPath("$.memberCount").value(2))
            // RG-05-13: members sorted by name
            .andExpect(jsonPath("$.members[0].id").value(alice.getId()))
            .andExpect(jsonPath("$.members[1].id").value(bob.getId()))
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.LOCATION);

    mockMvc
        .perform(as(get(location), admin, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Produit"))
        .andExpect(jsonPath("$.memberCount").value(2))
        .andExpect(jsonPath("$.members[0].active").value(true));
  }

  @Test
  @DisplayName("RG-05-14 / RG-00-04 : la création est tracée (équipe et rattachements)")
  void creationIsTraced() throws Exception {
    mockMvc
        .perform(
            as(post("/api/v1/teams"), admin, createBody("Produit", manager.getId(), alice.getId())))
        .andExpect(status().isCreated());

    Team team = teams.findAllActiveSorted().get(0);
    assertThat(team.getCreatedBy()).isEqualTo(admin.getId());
    assertThat(team.getUpdatedBy()).isEqualTo(admin.getId());
    var link = members.findByUserId(alice.getId()).orElseThrow();
    assertThat(link.getCreatedBy()).isEqualTo(admin.getId());
    assertThat(link.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("RG-05-07 / RG-05-06 : une équipe sans membre est admise, un manager en a plusieurs")
  void teamWithoutMembersAndManagerOfSeveral() throws Exception {
    mockMvc
        .perform(as(post("/api/v1/teams"), admin, createBody("Vide", manager.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.memberCount").value(0));
    mockMvc
        .perform(as(post("/api/v1/teams"), admin, createBody("Autre", manager.getId())))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("CA-05-02 : « PRODUIT » alors que « Produit » existe donne 409 RG-05-01 sur name")
  void duplicateNameIs409() throws Exception {
    mockMvc
        .perform(as(post("/api/v1/teams"), admin, createBody("Produit", manager.getId())))
        .andExpect(status().isCreated());

    mockMvc
        .perform(as(post("/api/v1/teams"), admin, createBody("PRODUIT", otherManager.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-01"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  @DisplayName("CA-05-02 : la contrainte unique en base (course) donne aussi 409 RG-05-01")
  void raceOnNameIndexIs409() throws Exception {
    mockMvc
        .perform(as(post("/api/v1/teams"), admin, createBody("Produit", manager.getId())))
        .andExpect(status().isCreated());
    Mockito.doReturn(false).when(teams).existsActiveByName(Mockito.anyString(), Mockito.anyLong());

    mockMvc
        .perform(as(post("/api/v1/teams"), admin, createBody("produit", otherManager.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-01"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  @DisplayName("RG-05-01 : un nom trop court ou trop long donne 400 sur le champ name")
  void invalidNameIs400() throws Exception {
    for (String bad : new String[] {"A", " a ", "x".repeat(101)}) {
      mockMvc
          .perform(as(post("/api/v1/teams"), admin, createBody(bad, manager.getId())))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("name"));
    }
    assertThat(teams.count()).isZero();
  }

  @Test
  @DisplayName("RG-05-01 : un corps sans manager ou sans liste de membres donne 400")
  void missingFieldsIs400() throws Exception {
    mockMvc
        .perform(as(post("/api/v1/teams"), admin, "{\"name\":\"Produit\",\"memberIds\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("managerId"));
    mockMvc
        .perform(
            as(
                post("/api/v1/teams"),
                admin,
                "{\"name\":\"Produit\",\"managerId\":" + manager.getId() + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("memberIds"));
  }

  @Test
  @DisplayName("CA-05-03 : désigner un compte USER comme manager donne 409 RG-05-02")
  void userAsManagerIs409() throws Exception {
    mockMvc
        .perform(as(post("/api/v1/teams"), admin, createBody("Produit", plainUser.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-02"))
        .andExpect(jsonPath("$.errors[0].field").value("managerId"));
    assertThat(teams.count()).isZero();
  }

  @Test
  @DisplayName("CA-05-03 : un manager désactivé ou un compte inconnu donne 409 RG-05-02")
  void inactiveOrUnknownManagerIs409() throws Exception {
    manager.setActive(false);
    users.save(manager);

    mockMvc
        .perform(as(post("/api/v1/teams"), admin, createBody("Produit", manager.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-02"));
    mockMvc
        .perform(as(post("/api/v1/teams"), admin, createBody("Produit", 999999L)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-02"));
  }

  @Test
  @DisplayName("CA-05-04 : un compte déjà membre de l'équipe A ajouté à B donne 409 RG-05-04 (A)")
  void memberOfAnotherTeamIs409() throws Exception {
    mockMvc
        .perform(
            as(
                post("/api/v1/teams"),
                admin,
                createBody("Équipe A", manager.getId(), alice.getId())))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            as(
                post("/api/v1/teams"),
                admin,
                createBody("Équipe B", otherManager.getId(), alice.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-04"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Équipe A")))
        .andExpect(jsonPath("$.errors[0].field").value("memberIds"));
    assertThat(teams.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-05-04 : la clé primaire en base (course) donne aussi 409 RG-05-04")
  void raceOnMemberPrimaryKeyIs409() throws Exception {
    mockMvc
        .perform(
            as(
                post("/api/v1/teams"),
                admin,
                createBody("Équipe A", manager.getId(), alice.getId())))
        .andExpect(status().isCreated());
    // the service check does not see the existing link: only the database refuses it
    Mockito.doReturn(List.of()).when(members).findWithTeamByUserIds(Mockito.anyCollection());

    mockMvc
        .perform(
            as(
                post("/api/v1/teams"),
                admin,
                createBody("Équipe B", otherManager.getId(), alice.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-04"))
        .andExpect(jsonPath("$.errors[0].field").value("memberIds"));
  }

  @Test
  @DisplayName("CA-05-05 : désigner un ADMIN comme membre donne 409 RG-05-03")
  void adminAsMemberIs409() throws Exception {
    mockMvc
        .perform(
            as(
                post("/api/v1/teams"),
                admin,
                createBody("Produit", manager.getId(), otherAdmin.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-03"))
        .andExpect(jsonPath("$.errors[0].field").value("memberIds"));
  }

  @Test
  @DisplayName("CA-05-06 : le manager choisi comme membre de sa propre équipe donne 409 RG-05-05")
  void managerAsMemberIs409() throws Exception {
    mockMvc
        .perform(
            as(
                post("/api/v1/teams"),
                admin,
                createBody("Produit", manager.getId(), manager.getId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-05-05"))
        .andExpect(jsonPath("$.errors[0].field").value("memberIds"));
  }

  @Test
  @DisplayName("RG-05-05 : le manager d'une équipe peut être membre d'une autre équipe")
  void managerMemberOfAnotherTeam() throws Exception {
    mockMvc
        .perform(as(post("/api/v1/teams"), admin, createBody("Produit", manager.getId())))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            as(
                post("/api/v1/teams"),
                admin,
                createBody("Technique", otherManager.getId(), manager.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.memberCount").value(1));
  }

  @Test
  @DisplayName("RG-05-09 : lire une équipe inconnue donne 404")
  void unknownTeamIs404() throws Exception {
    mockMvc.perform(as(get("/api/v1/teams/999999"), admin, null)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-05-11 : createTeam et getTeam répondent 401 sans token")
  void unauthenticatedIs401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("Produit", manager.getId())))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/teams/1")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CA-05-11 : createTeam et getTeam répondent 403 à un USER ou un MANAGER")
  void nonAdminIs403() throws Exception {
    for (User caller : new User[] {plainUser, manager}) {
      mockMvc
          .perform(as(post("/api/v1/teams"), caller, createBody("Produit", manager.getId())))
          .andExpect(status().isForbidden());
      mockMvc.perform(as(get("/api/v1/teams/1"), caller, null)).andExpect(status().isForbidden());
    }
    assertThat(teams.count()).isZero();
  }
}
