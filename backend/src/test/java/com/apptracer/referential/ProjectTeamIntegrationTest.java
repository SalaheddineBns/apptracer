package com.apptracer.referential;

import static org.hamcrest.Matchers.contains;
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
import com.jayway.jsonpath.JsonPath;
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

/**
 * SFD-10 : jeu commun des CA. Équipe Dev (manager Marion, membre Alice) avec ALPHA, équipe QA
 * (manager Quentin, membre Bruno) avec BETA, COMMUN sans équipe, Chloé sans équipe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProjectTeamIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User admin;
  private User marion;
  private User quentin;
  private User alice;
  private User bruno;
  private User chloe;
  private long devId;
  private long qaId;
  private long alphaId;

  @BeforeEach
  void setUp() throws Exception {
    admin = users.save(new User("pt-admin@example.com", "Ada", "Admin", "hash", Role.ADMIN));
    marion = users.save(new User("pt-marion@example.com", "Marion", "Leroy", "hash", Role.MANAGER));
    quentin =
        users.save(new User("pt-quentin@example.com", "Quentin", "Roux", "hash", Role.MANAGER));
    alice = users.save(new User("pt-alice@example.com", "Alice", "Martin", "hash", Role.USER));
    bruno = users.save(new User("pt-bruno@example.com", "Bruno", "Petit", "hash", Role.USER));
    chloe = users.save(new User("pt-chloe@example.com", "Chloé", "Moreau", "hash", Role.USER));
    devId = createTeam("Dev", marion.getId(), alice.getId());
    qaId = createTeam("QA", quentin.getId(), bruno.getId());
    alphaId = createProject("ALPHA", "Alpha", devId);
    createProject("BETA", "Beta", qaId);
    createProject("COMMUN", "Commun", null);
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM time_entries");
    jdbc.execute("DELETE FROM projects");
    jdbc.execute("DELETE FROM team_members");
    jdbc.execute("DELETE FROM teams");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  private String bearer(User user) {
    return "Bearer " + tokens.issue(user.getId(), user.getRole(), UUID.randomUUID());
  }

  private ResultActions send(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
      User user,
      String json)
      throws Exception {
    return mockMvc.perform(
        request
            .header(HttpHeaders.AUTHORIZATION, bearer(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private long createTeam(String name, Long managerId, Long memberId) throws Exception {
    String body =
        send(
                post("/api/v1/teams"),
                admin,
                "{\"name\":\""
                    + name
                    + "\",\"managerId\":"
                    + managerId
                    + ",\"memberIds\":["
                    + memberId
                    + "]}")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  private long createProject(String code, String name, Long teamId) throws Exception {
    String body =
        send(
                post("/api/v1/projects"),
                admin,
                "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"teamId\":" + teamId + "}")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  private String hourEntry(String projectCode, String comment) {
    return "{\"granularity\":\"HOUR\",\"startDate\":\"2026-10-05\",\"startTime\":\"09:00\","
        + "\"endTime\":\"10:00\",\"activityTypeCode\":\"TRAVAIL_PROJET\",\"projectCode\":\""
        + projectCode
        + "\",\"comment\":\""
        + comment
        + "\"";
  }

  private ResultActions listProjects(User user) throws Exception {
    return mockMvc.perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, bearer(user)));
  }

  @Test
  @DisplayName("CA-10-01 : l'ADMIN affecte ALPHA à Dev, la liste des projets affiche son équipe")
  void adminSeesTheTeamOfEachProject() throws Exception {
    listProjects(admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.code == 'ALPHA')].team.name").value(contains("Dev")))
        .andExpect(jsonPath("$[?(@.code == 'COMMUN')].team").value(contains((Object) null)));
  }

  @Test
  @DisplayName("CA-10-02 : chacun ne voit que les projets de ses équipes et les projets communs")
  void eachAccountSeesItsAllowedProjects() throws Exception {
    listProjects(alice).andExpect(jsonPath("$[*].code").value(contains("ALPHA", "COMMUN")));
    listProjects(bruno).andExpect(jsonPath("$[*].code").value(contains("BETA", "COMMUN")));
    listProjects(chloe).andExpect(jsonPath("$[*].code").value(contains("COMMUN")));
    listProjects(marion).andExpect(jsonPath("$[*].code").value(contains("ALPHA", "COMMUN")));
  }

  @Test
  @DisplayName("CA-10-03 : Alice ne peut pas saisir sur BETA (409 RG-10-04 sur projectCode)")
  void refusesAProjectOfAnotherTeam() throws Exception {
    send(post("/api/v1/time-entries"), alice, hourEntry("BETA", "") + "}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-10-04"))
        .andExpect(jsonPath("$.errors[0].field").value("projectCode"));
    send(post("/api/v1/time-entries"), alice, hourEntry("COMMUN", "") + "}")
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "CA-10-04 : ALPHA passe à QA, la saisie d'Alice reste modifiable si le projet ne change pas")
  void anExistingEntryKeepsItsProject() throws Exception {
    String created =
        send(post("/api/v1/time-entries"), alice, hourEntry("ALPHA", "avant") + "}")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long entryId = ((Number) JsonPath.read(created, "$.id")).longValue();
    long version = ((Number) JsonPath.read(created, "$.version")).longValue();

    send(
            put("/api/v1/projects/" + alphaId),
            admin,
            "{\"name\":\"Alpha\",\"teamId\":" + qaId + ",\"version\":0}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.team.name").value("QA"));

    mockMvc
        .perform(
            get("/api/v1/time-entries?weekStart=2026-10-05")
                .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$..project.code").value(contains("ALPHA")));
    send(
            put("/api/v1/time-entries/" + entryId),
            alice,
            hourEntry("ALPHA", "après") + ",\"version\":" + version + "}")
        .andExpect(status().isOk());
    send(
            put("/api/v1/time-entries/" + entryId),
            alice,
            hourEntry("BETA", "après") + ",\"version\":" + (version + 1) + "}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-10-04"));
  }

  @Test
  @DisplayName("CA-10-05 : à la suppression de QA, BETA devient sans équipe et Chloé le voit")
  void deletingATeamReleasesItsProjects() throws Exception {
    mockMvc
        .perform(delete("/api/v1/teams/" + qaId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isNoContent());

    listProjects(chloe).andExpect(jsonPath("$[*].code").value(contains("BETA", "COMMUN")));
  }

  @Test
  @DisplayName("CA-10-06 : désactiver puis réactiver ALPHA ne change pas son équipe")
  void reactivationKeepsTheTeam() throws Exception {
    send(post("/api/v1/projects/" + alphaId + "/deactivation"), admin, "")
        .andExpect(status().isOk());
    send(post("/api/v1/projects/" + alphaId + "/reactivation"), admin, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.team.name").value("Dev"));
  }

  @Test
  @DisplayName("CA-10-07 : un USER ou un MANAGER ne peut pas changer l'équipe d'un projet (403)")
  void onlyAnAdminAssignsATeam() throws Exception {
    for (User user : new User[] {alice, marion}) {
      send(
              put("/api/v1/projects/" + alphaId),
              user,
              "{\"name\":\"Alpha\",\"teamId\":" + qaId + ",\"version\":0}")
          .andExpect(status().isForbidden());
    }
  }

  @Test
  @DisplayName("RG-10-01 : une équipe inconnue est refusée (409 sur teamId)")
  void refusesAnUnknownTeam() throws Exception {
    send(
            post("/api/v1/projects"),
            admin,
            "{\"code\":\"GAMMA\",\"name\":\"Gamma\",\"teamId\":999999}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-10-01"))
        .andExpect(jsonPath("$.errors[0].field").value("teamId"));
  }

  private ResultActions myProjects(User user) throws Exception {
    return mockMvc.perform(
        get("/api/v1/projects/mine").header(HttpHeaders.AUTHORIZATION, bearer(user)));
  }

  @Test
  @DisplayName(
      "CA-10-09 : « Mes projets » groupe les projets par équipe puis les projets communs ; 403 pour l'ADMIN")
  void myProjectsAreGroupedByTeam() throws Exception {
    myProjects(alice)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teams[*].team.name").value(contains("Dev")))
        .andExpect(jsonPath("$.teams[0].projects[*].code").value(contains("ALPHA")))
        .andExpect(jsonPath("$.sharedProjects[*].code").value(contains("COMMUN")));
    myProjects(chloe)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teams").isEmpty())
        .andExpect(jsonPath("$.sharedProjects[*].code").value(contains("COMMUN")));
    myProjects(marion)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teams[*].team.name").value(contains("Dev")))
        .andExpect(jsonPath("$.teams[0].projects[*].code").value(contains("ALPHA")))
        .andExpect(jsonPath("$.sharedProjects[*].code").value(contains("COMMUN")));
    myProjects(admin).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/projects/mine")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "RG-10-11 : une équipe sans projet actif reste affichée, vide ; les projets inactifs n'apparaissent pas")
  void aTeamWithoutActiveProjectIsListedEmpty() throws Exception {
    send(post("/api/v1/projects/" + alphaId + "/deactivation"), admin, "")
        .andExpect(status().isOk());

    myProjects(alice)
        .andExpect(jsonPath("$.teams[*].team.name").value(contains("Dev")))
        .andExpect(jsonPath("$.teams[0].projects").isEmpty());
  }
}
