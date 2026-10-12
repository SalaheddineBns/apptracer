package com.apptracer.referential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.referential.domain.Project;
import com.apptracer.referential.repository.ProjectRepository;
import com.apptracer.security.AccessTokenService;
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
class ProjectControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @MockitoSpyBean private ProjectRepository projects;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User admin;
  private User plainUser;
  private User manager;

  @BeforeEach
  void setUp() {
    admin = users.save(new User("pj-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    plainUser = users.save(new User("pj-user@example.com", "U", "User", "hash", Role.USER));
    manager = users.save(new User("pj-mgr@example.com", "M", "Mgr", "hash", Role.MANAGER));
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM projects");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
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

  private static String createBody(String code) {
    return "{\"code\":\"" + code + "\",\"name\":\"Projet test\"}";
  }

  private static String updateBody(String name, long version) {
    return "{\"name\":\"" + name + "\",\"version\":" + version + "}";
  }

  @Test
  @DisplayName("RG-02-01 / RG-00-04 : l'ADMIN crée un projet (201, Location, actif, traçabilité)")
  void adminCreates() throws Exception {
    mockMvc
        .perform(as(post("/api/v1/projects"), admin, createBody("P1")))
        .andExpect(status().isCreated())
        .andExpect(header().exists(HttpHeaders.LOCATION))
        .andExpect(jsonPath("$.code").value("P1"))
        .andExpect(jsonPath("$.name").value("Projet test"))
        .andExpect(jsonPath("$.active").value(true));
    Project p = projects.findByCode("P1").orElseThrow();
    assertThat(p.getCreatedBy()).isEqualTo(admin.getId());
    assertThat(p.getUpdatedBy()).isEqualTo(admin.getId());
  }

  @Test
  @DisplayName("CA-02-03 : un code de projet déjà existant donne 409 RG-02-01 sur le champ code")
  void duplicateCodeIs409() throws Exception {
    projects.saveAndFlush(new Project("P1", "Existant"));
    mockMvc
        .perform(as(post("/api/v1/projects"), admin, createBody("P1")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-02-01"))
        .andExpect(jsonPath("$.errors[0].field").value("code"));
  }

  @Test
  @DisplayName(
      "CA-02-03 : la violation de la contrainte unique en base (course) donne aussi 409 RG-02-01")
  void raceOnUniqueIndexIs409() throws Exception {
    projects.saveAndFlush(new Project("P1", "Existant"));
    Mockito.doReturn(false).when(projects).existsByCode("P1");
    mockMvc
        .perform(as(post("/api/v1/projects"), admin, createBody("P1")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-02-01"))
        .andExpect(jsonPath("$.errors[0].field").value("code"));
  }

  @Test
  @DisplayName("CA-02-04 : un code invalide (« réunion client ») donne 400 avec le champ en erreur")
  void invalidCodeIs400() throws Exception {
    for (String bad : new String[] {"réunion client", "projet", "A", "A".repeat(31), "A-B"}) {
      mockMvc
          .perform(as(post("/api/v1/projects"), admin, createBody(bad)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("code"));
    }
    assertThat(projects.count()).isZero();
  }

  @Test
  @DisplayName("RG-02-01 : un nom de plus de 100 caractères ou vide donne 400 sur le champ name")
  void invalidNameIs400() throws Exception {
    for (String bad : new String[] {"x".repeat(101), " "}) {
      mockMvc
          .perform(
              as(post("/api/v1/projects"), admin, "{\"code\":\"P1\",\"name\":\"" + bad + "\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("name"));
    }
  }

  @Test
  @DisplayName(
      "RG-02-01 / RG-00-04 : l'ADMIN modifie le nom, le code reste, updated_by est l'ADMIN")
  void adminUpdatesAndCodeIsIgnored() throws Exception {
    Project p = projects.saveAndFlush(new Project("P1", "Ancien"));
    String body = "{\"code\":\"AUTRE\",\"name\":\"Nouveau\",\"version\":0}";
    mockMvc
        .perform(as(put("/api/v1/projects/" + p.getId()), admin, body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("P1"))
        .andExpect(jsonPath("$.name").value("Nouveau"))
        .andExpect(jsonPath("$.version").value(1));
    assertThat(projects.findById(p.getId()).orElseThrow().getUpdatedBy()).isEqualTo(admin.getId());
  }

  @Test
  @DisplayName("Modification concurrente : une version périmée donne 409")
  void staleVersionIs409() throws Exception {
    Project p = projects.saveAndFlush(new Project("P1", "N"));
    mockMvc
        .perform(as(put("/api/v1/projects/" + p.getId()), admin, updateBody("X", 7)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  @Test
  @DisplayName("RG-02-08 : l'ADMIN lit un projet ; 404 pour un identifiant inconnu")
  void getAndNotFound() throws Exception {
    Project p = projects.saveAndFlush(new Project("P1", "N"));
    mockMvc
        .perform(as(get("/api/v1/projects/" + p.getId()), admin, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("P1"));
    mockMvc
        .perform(as(get("/api/v1/projects/999999"), admin, null))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(as(put("/api/v1/projects/999999"), admin, updateBody("x", 0)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(as(post("/api/v1/projects/999999/deactivation"), admin, null))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(as(post("/api/v1/projects/999999/reactivation"), admin, null))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "CA-02-06 : un projet désactivé disparaît pour USER, reste visible inactif pour l'ADMIN")
  void deactivatedProjectHiddenFromUser() throws Exception {
    Project p1 = projects.saveAndFlush(new Project("P1", "Projet un"));
    projects.saveAndFlush(new Project("P2", "Projet deux"));

    mockMvc
        .perform(as(post("/api/v1/projects/" + p1.getId() + "/deactivation"), admin, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    assertThat(projects.findById(p1.getId()).orElseThrow().getUpdatedBy()).isEqualTo(admin.getId());

    for (User u : new User[] {plainUser, manager}) {
      mockMvc
          .perform(as(get("/api/v1/projects"), u, null))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].code").value("P2"));
    }
    mockMvc
        .perform(as(get("/api/v1/projects"), admin, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[?(@.code == 'P1')].active").value(false));
  }

  @Test
  @DisplayName("CA-02-08 : un projet réactivé est de nouveau proposé aux USER")
  void reactivatedProjectProposedAgain() throws Exception {
    Project p = new Project("P1", "Projet un");
    p.setActive(false);
    p = projects.saveAndFlush(p);

    mockMvc
        .perform(as(get("/api/v1/projects"), plainUser, null))
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(as(post("/api/v1/projects/" + p.getId() + "/reactivation"), admin, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));
    mockMvc
        .perform(as(get("/api/v1/projects"), plainUser, null))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value("P1"));
  }

  @Test
  @DisplayName("RG-02-05 : désactivation et réactivation redondantes répondent 200 sans effet")
  void redundantToggleIs200() throws Exception {
    Project p = projects.saveAndFlush(new Project("P1", "N"));
    String url = "/api/v1/projects/" + p.getId();
    mockMvc
        .perform(as(post(url + "/reactivation"), admin, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(0));
    mockMvc.perform(as(post(url + "/deactivation"), admin, null));
    mockMvc
        .perform(as(post(url + "/deactivation"), admin, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  @DisplayName("RG-02-05 : aucun endpoint de suppression n'est exposé")
  void noDeleteEndpoint() throws Exception {
    Project p = projects.saveAndFlush(new Project("P1", "N"));
    mockMvc
        .perform(as(delete("/api/v1/projects/" + p.getId()), admin, null))
        .andExpect(status().is4xxClientError());
    assertThat(projects.findByCode("P1")).isPresent();
  }

  @Test
  @DisplayName("CA-02-09 : USER et MANAGER reçoivent 403 sur chaque endpoint de projet réservé")
  void nonAdminIs403() throws Exception {
    Project p = projects.saveAndFlush(new Project("P1", "N"));
    String url = "/api/v1/projects/" + p.getId();
    for (User u : new User[] {plainUser, manager}) {
      mockMvc
          .perform(as(post("/api/v1/projects"), u, createBody("INTERDIT")))
          .andExpect(status().isForbidden());
      mockMvc.perform(as(get(url), u, null)).andExpect(status().isForbidden());
      mockMvc.perform(as(put(url), u, updateBody("x", 0))).andExpect(status().isForbidden());
      mockMvc.perform(as(post(url + "/deactivation"), u, null)).andExpect(status().isForbidden());
      mockMvc.perform(as(post(url + "/reactivation"), u, null)).andExpect(status().isForbidden());
    }
    assertThat(projects.findByCode("INTERDIT")).isEmpty();
    assertThat(projects.findByCode("P1").orElseThrow().isActive()).isTrue();
  }

  @Test
  @DisplayName("CA-02-11 : sans jeton, chaque endpoint de projet répond 401")
  void unauthenticatedIs401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("ANON")))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/projects/1")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put("/api/v1/projects/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("x", 0)))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v1/projects/1/deactivation")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v1/projects/1/reactivation")).andExpect(status().isUnauthorized());
  }
}
