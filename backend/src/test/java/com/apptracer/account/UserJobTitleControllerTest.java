package com.apptracer.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.referential.domain.JobTitle;
import com.apptracer.referential.repository.JobTitleRepository;
import com.apptracer.security.AccessTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserJobTitleControllerTest {

  private static final String PASSWORD = "Motdepasse12345";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private JobTitleRepository titles;
  @Autowired private PasswordEncoder encoder;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AccessTokenService tokens;

  private User admin;
  private User jean;
  private User other;

  @BeforeEach
  void setUp() {
    admin = save("jt-admin@example.com", "Ada", "Admin", Role.ADMIN);
    jean = save("jt-jean@example.com", "Jean", "Dupont", Role.USER);
    other = save("jt-other@example.com", "Olive", "Autre", Role.USER);
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM time_entries");
    jdbc.execute("DELETE FROM team_members");
    jdbc.execute("DELETE FROM teams");
    jdbc.execute("UPDATE users SET job_title_id = NULL");
    jdbc.execute(
        "DELETE FROM job_titles WHERE code NOT IN"
            + " ('DEVELOPER','TECH_LEAD','QA','SCRUM_MASTER','PRODUCT_OWNER','DATA_ANALYST')");
    jdbc.execute(
        "UPDATE job_titles SET active = TRUE, version = 0, created_by = NULL, updated_by = NULL");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  private User save(String email, String first, String last, Role role) {
    return users.save(new User(email, first, last, encoder.encode(PASSWORD), role));
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, User actor) {
    return b.header(
        HttpHeaders.AUTHORIZATION,
        "Bearer " + tokens.issue(actor.getId(), actor.getRole(), UUID.randomUUID()));
  }

  private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
    return b.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static String quoted(String code) {
    return code == null ? "null" : "\"" + code + "\"";
  }

  private static String updateBody(String role, String jobTitleCode, long version) {
    return "{\"firstName\":\"Jean\",\"lastName\":\"Dupont\",\"role\":\"%s\",\"jobTitleCode\":%s,\"version\":%d}"
        .formatted(role, quoted(jobTitleCode), version);
  }

  private static String createBody(String email, String role, String jobTitleCode) {
    return ("{\"email\":\"%s\",\"firstName\":\"N\",\"lastName\":\"M\",\"role\":\"%s\","
            + "\"initialPassword\":\"%s\",\"jobTitleCode\":%s}")
        .formatted(email, role, PASSWORD, quoted(jobTitleCode));
  }

  private long version(User u) {
    return users.findById(u.getId()).orElseThrow().getVersion();
  }

  @Test
  @DisplayName(
      "CA-09-04 : l'ADMIN attribue « Developer » à un USER, la réponse et la liste l'affichent, traçabilité")
  void adminAssignsJobTitleToUser() throws Exception {
    mockMvc
        .perform(
            json(
                as(put("/api/v1/users/" + jean.getId()), admin),
                updateBody("USER", "DEVELOPER", 0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobTitle.code").value("DEVELOPER"))
        .andExpect(jsonPath("$.jobTitle.label").value("Developer"))
        .andExpect(jsonPath("$.jobTitle.active").value(true));
    assertThat(users.findById(jean.getId()).orElseThrow().getUpdatedBy())
        .isEqualTo(admin.getId()); // RG-09-11
    mockMvc
        .perform(as(get("/api/v1/users"), admin))
        .andExpect(
            jsonPath("$.content[?(@.email == 'jt-jean@example.com')].jobTitle.label")
                .value("Developer"));
  }

  @Test
  @DisplayName("CA-09-04 : à la création, la fonction est facultative (absente, ou donnée)")
  void jobTitleIsOptionalOnCreate() throws Exception {
    mockMvc
        .perform(json(as(post("/api/v1/users"), admin), createBody("a1@example.com", "USER", null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.jobTitle").doesNotExist());
    mockMvc
        .perform(
            json(as(post("/api/v1/users"), admin), createBody("a2@example.com", "MANAGER", "QA")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.jobTitle.code").value("QA"));
    mockMvc
        .perform(
            json(
                as(post("/api/v1/users"), admin),
                "{\"email\":\"a3@example.com\",\"firstName\":\"N\",\"lastName\":\"M\","
                    + "\"role\":\"USER\",\"initialPassword\":\""
                    + PASSWORD
                    + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.jobTitle").doesNotExist());
  }

  @Test
  @DisplayName(
      "CA-09-04 / RG-09-01 : un USER « Developer » ne voit pas la vue équipe et ne modifie pas la saisie d'un autre (403)")
  void jobTitleGivesNoRights() throws Exception {
    jean.setJobTitle(titles.findByCode("DEVELOPER").orElseThrow());
    jean = users.saveAndFlush(jean);
    String entry =
        mockMvc
            .perform(
                json(
                    as(post("/api/v1/time-entries"), other),
                    "{\"granularity\":\"DAY\",\"startDate\":\""
                        + LocalDate.now().minusDays(20)
                        + "\",\"activityTypeCode\":\"REUNION\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long entryId = new ObjectMapper().readTree(entry).get("id").asLong();

    mockMvc
        .perform(
            json(
                as(put("/api/v1/time-entries/" + entryId), jean),
                "{\"granularity\":\"DAY\",\"startDate\":\""
                    + LocalDate.now().minusDays(20)
                    + "\",\"activityTypeCode\":\"REUNION\",\"version\":0}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(as(get("/api/v1/team-view/teams"), jean)).andExpect(status().isForbidden());
    mockMvc.perform(as(get("/api/v1/users"), jean)).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "CA-09-05 : un USER « Tech Lead » passé MANAGER et manager d'une équipe garde sa fonction et accède à la vue équipe")
  void roleChangeKeepsJobTitleAndTeamViewFollowsRole() throws Exception {
    jean.setJobTitle(titles.findByCode("TECH_LEAD").orElseThrow());
    jean = users.saveAndFlush(jean);
    mockMvc.perform(as(get("/api/v1/team-view/teams"), jean)).andExpect(status().isForbidden());

    mockMvc
        .perform(
            json(
                as(put("/api/v1/users/" + jean.getId()), admin),
                updateBody("MANAGER", "TECH_LEAD", version(jean))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("MANAGER"))
        .andExpect(jsonPath("$.jobTitle.code").value("TECH_LEAD")); // RG-09-03
    mockMvc
        .perform(
            json(
                as(post("/api/v1/teams"), admin),
                "{\"name\":\"Équipe JT\",\"managerId\":" + jean.getId() + ",\"memberIds\":[]}"))
        .andExpect(status().isCreated());

    User manager = users.findById(jean.getId()).orElseThrow();
    mockMvc
        .perform(as(get("/api/v1/team-view/teams"), manager))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    assertThat(manager.getJobTitle().getCode()).isEqualTo("TECH_LEAD");

    // une modification qui garde le rôle MANAGER laisse la fonction
    mockMvc
        .perform(
            json(
                as(put("/api/v1/users/" + jean.getId()), admin),
                updateBody("MANAGER", "TECH_LEAD", version(jean))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobTitle.code").value("TECH_LEAD"));
  }

  @Test
  @DisplayName(
      "CA-09-06 : une fonction pour un ADMIN est refusée (409 RG-09-02, champ jobTitleCode)")
  void jobTitleForAdminIs409() throws Exception {
    // création d'un ADMIN avec une fonction
    mockMvc
        .perform(
            json(as(post("/api/v1/users"), admin), createBody("adm2@example.com", "ADMIN", "QA")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-09-02"))
        .andExpect(jsonPath("$.errors[0].field").value("jobTitleCode"));
    // modification d'un ADMIN existant
    User admin2 = save("jt-admin2@example.com", "B", "Admin", Role.ADMIN);
    mockMvc
        .perform(
            json(
                as(put("/api/v1/users/" + admin2.getId()), admin),
                updateBody("ADMIN", "QA", version(admin2))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-09-02"))
        .andExpect(jsonPath("$.errors[0].field").value("jobTitleCode"));
    // promotion d'un USER en ADMIN en lui envoyant une fonction
    mockMvc
        .perform(
            json(
                as(put("/api/v1/users/" + jean.getId()), admin),
                updateBody("ADMIN", "QA", version(jean))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("jobTitleCode"));
    assertThat(users.findById(admin2.getId()).orElseThrow().getJobTitle()).isNull();
    assertThat(users.findById(jean.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
  }

  @Test
  @DisplayName(
      "CA-09-07 : une fonction inactive est refusée pour un autre compte (409), gardée par celui qui la porte, de nouveau proposée une fois réactivée")
  void inactiveJobTitleRules() throws Exception {
    jean.setJobTitle(titles.findByCode("QA").orElseThrow());
    jean = users.saveAndFlush(jean);
    JobTitle qa = titles.findByCode("QA").orElseThrow();
    mockMvc
        .perform(as(post("/api/v1/job-titles/" + qa.getId() + "/deactivation"), admin))
        .andExpect(status().isOk());

    // le porteur affiche « QA » inactive
    mockMvc
        .perform(as(get("/api/v1/users/" + jean.getId()), admin))
        .andExpect(jsonPath("$.jobTitle.code").value("QA"))
        .andExpect(jsonPath("$.jobTitle.active").value(false));
    // un autre compte ne peut pas la recevoir (modification et création)
    mockMvc
        .perform(
            json(
                as(put("/api/v1/users/" + other.getId()), admin),
                updateBody("USER", "QA", version(other))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-09-06"))
        .andExpect(jsonPath("$.errors[0].field").value("jobTitleCode"));
    mockMvc
        .perform(json(as(post("/api/v1/users"), admin), createBody("n1@example.com", "USER", "QA")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("jobTitleCode"));
    // le porteur peut la garder à la modification, ou en choisir une active
    mockMvc
        .perform(
            json(
                as(put("/api/v1/users/" + jean.getId()), admin),
                updateBody("MANAGER", "QA", version(jean))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobTitle.code").value("QA"))
        .andExpect(jsonPath("$.jobTitle.active").value(false));
    mockMvc
        .perform(
            json(
                as(put("/api/v1/users/" + jean.getId()), admin),
                updateBody("MANAGER", "DEVELOPER", version(jean))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobTitle.code").value("DEVELOPER"));
    // une fois réactivée, elle est de nouveau attribuable
    mockMvc
        .perform(as(post("/api/v1/job-titles/" + qa.getId() + "/reactivation"), admin))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            json(
                as(put("/api/v1/users/" + other.getId()), admin),
                updateBody("USER", "QA", version(other))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobTitle.active").value(true));
  }

  @Test
  @DisplayName("RG-09-06 : une fonction inconnue est refusée (409, champ jobTitleCode)")
  void unknownJobTitleIs409() throws Exception {
    mockMvc
        .perform(
            json(
                as(put("/api/v1/users/" + jean.getId()), admin),
                updateBody("USER", "INEXISTANTE", 0)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-09-06"))
        .andExpect(jsonPath("$.errors[0].field").value("jobTitleCode"));
  }

  @Test
  @DisplayName(
      "CA-09-08 : le passage au rôle ADMIN retire la fonction ; revenir à USER ne la rétablit pas")
  void promotionToAdminRemovesJobTitle() throws Exception {
    jean.setJobTitle(titles.findByCode("DEVELOPER").orElseThrow());
    jean = users.saveAndFlush(jean);
    mockMvc
        .perform(
            json(
                as(put("/api/v1/users/" + jean.getId()), admin),
                updateBody("ADMIN", null, version(jean))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.jobTitle").doesNotExist());
    assertThat(users.findById(jean.getId()).orElseThrow().getJobTitle()).isNull();
    mockMvc
        .perform(
            json(
                as(put("/api/v1/users/" + jean.getId()), admin),
                updateBody("USER", null, version(jean))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobTitle").doesNotExist());
  }

  @Test
  @DisplayName("RG-09-04 : l'ADMIN peut retirer la fonction d'un compte (sans jobTitleCode)")
  void adminRemovesJobTitle() throws Exception {
    jean.setJobTitle(titles.findByCode("DEVELOPER").orElseThrow());
    jean = users.saveAndFlush(jean);
    mockMvc
        .perform(
            json(
                as(put("/api/v1/users/" + jean.getId()), admin),
                "{\"firstName\":\"Jean\",\"lastName\":\"Dupont\",\"role\":\"USER\",\"version\":"
                    + version(jean)
                    + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobTitle").doesNotExist());
  }

  @Test
  @DisplayName(
      "RG-09-04 : USER et MANAGER reçoivent 403 en tentant de changer une fonction (la leur y compris)")
  void nonAdminCannotChangeJobTitle() throws Exception {
    User manager = save("jt-mgr@example.com", "M", "Mgr", Role.MANAGER);
    for (User actor : new User[] {jean, manager}) {
      mockMvc
          .perform(
              json(
                  as(put("/api/v1/users/" + actor.getId()), actor),
                  updateBody(actor.getRole().name(), "QA", 0)))
          .andExpect(status().isForbidden());
    }
    assertThat(users.findById(jean.getId()).orElseThrow().getJobTitle()).isNull();
  }
}
