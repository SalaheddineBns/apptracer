package com.apptracer.referential;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.domain.TagColor;
import com.apptracer.referential.repository.ActivityTypeRepository;
import com.apptracer.referential.service.ActivityTypeUsageChecker;
import com.apptracer.security.AccessTokenService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ActivityTypeControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  private ActivityTypeRepository types;

  @Autowired private ActivityTypeUsageChecker usageChecker;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User admin;
  private User plainUser;
  private User manager;

  @BeforeEach
  void setUp() {
    admin = users.save(new User("at-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    plainUser = users.save(new User("at-user@example.com", "U", "User", "hash", Role.USER));
    manager = users.save(new User("at-mgr@example.com", "M", "Mgr", "hash", Role.MANAGER));
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute(
        "DELETE FROM activity_types WHERE code NOT IN"
            + " ('TRAVAIL_PROJET','REUNION','LECTURE_SFD','REVUE_MR','FORMATION','AUTRE')");
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
    return "{\"code\":\""
        + code
        + "\",\"label\":\"Atelier\",\"projectRequired\":true,\"color\":\"INFO\"}";
  }

  private static String updateBody(String label, boolean projectRequired, long version) {
    return "{\"label\":\""
        + label
        + "\",\"projectRequired\":"
        + projectRequired
        + ",\"color\":\"WARN\",\"version\":"
        + version
        + "}";
  }

  @Test
  @DisplayName("RG-02-01 / RG-00-04 : l'ADMIN crée un type (201, Location, actif, traçabilité)")
  void adminCreates() throws Exception {
    mockMvc
        .perform(as(post("/api/v1/activity-types"), admin, createBody("ATELIER")))
        .andExpect(status().isCreated())
        .andExpect(header().exists(HttpHeaders.LOCATION))
        .andExpect(jsonPath("$.code").value("ATELIER"))
        .andExpect(jsonPath("$.label").value("Atelier"))
        .andExpect(jsonPath("$.projectRequired").value(true))
        .andExpect(jsonPath("$.color").value("INFO"))
        .andExpect(jsonPath("$.active").value(true));
    ActivityType t = types.findByCode("ATELIER").orElseThrow();
    assertThat(t.getCreatedBy()).isEqualTo(admin.getId());
    assertThat(t.getUpdatedBy()).isEqualTo(admin.getId());
  }

  @Test
  @DisplayName("CA-02-03 : un code déjà existant donne 409 RG-02-01 sur le champ code")
  void duplicateCodeIs409() throws Exception {
    mockMvc
        .perform(as(post("/api/v1/activity-types"), admin, createBody("REUNION")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-02-01"))
        .andExpect(jsonPath("$.errors[0].field").value("code"));
  }

  @Test
  @DisplayName(
      "CA-02-03 : la violation de la contrainte unique en base (course) donne aussi 409 RG-02-01")
  void raceOnUniqueIndexIs409() throws Exception {
    // le contrôle de service ne voit pas le doublon : seul l'index unique le refuse
    org.mockito.Mockito.doReturn(false).when(types).existsByCode("REUNION");
    mockMvc
        .perform(as(post("/api/v1/activity-types"), admin, createBody("REUNION")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-02-01"))
        .andExpect(jsonPath("$.errors[0].field").value("code"));
  }

  @Test
  @DisplayName("CA-02-04 : un code invalide (« réunion client ») donne 400 avec le champ en erreur")
  void invalidCodeIs400() throws Exception {
    for (String bad : new String[] {"réunion client", "reunion", "A", "A".repeat(31), "A-B"}) {
      mockMvc
          .perform(as(post("/api/v1/activity-types"), admin, createBody(bad)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("code"));
    }
    assertThat(types.findByCode("REUNION CLIENT")).isEmpty();
    assertThat(types.count()).isEqualTo(6);
  }

  @Test
  @DisplayName("RG-02-04 : une couleur inconnue donne 400")
  void unknownColorIs400() throws Exception {
    String body =
        "{\"code\":\"TEST_C\",\"label\":\"x\",\"projectRequired\":false,\"color\":\"DANGER\"}";
    mockMvc
        .perform(as(post("/api/v1/activity-types"), admin, body))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("RG-02-03 : projectRequired, libellé et couleur sont obligatoires (400)")
  void missingFieldsIs400() throws Exception {
    mockMvc
        .perform(as(post("/api/v1/activity-types"), admin, "{\"code\":\"VIDE\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "RG-02-01 / RG-00-04 : l'ADMIN modifie libellé et couleur, le code reste, updated_by est l'ADMIN")
  void adminUpdates() throws Exception {
    ActivityType t =
        types.saveAndFlush(new ActivityType("A_MODIFIER", "Ancien", false, TagColor.INFO));
    mockMvc
        .perform(
            as(put("/api/v1/activity-types/" + t.getId()), admin, updateBody("Nouveau", false, 0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("A_MODIFIER"))
        .andExpect(jsonPath("$.label").value("Nouveau"))
        .andExpect(jsonPath("$.color").value("WARN"))
        .andExpect(jsonPath("$.version").value(1));
    ActivityType reloaded = types.findById(t.getId()).orElseThrow();
    assertThat(reloaded.getUpdatedBy()).isEqualTo(admin.getId());
  }

  @Test
  @DisplayName("RG-02-01 : un code envoyé à la modification est ignoré")
  void updateIgnoresCode() throws Exception {
    ActivityType t = types.saveAndFlush(new ActivityType("FIXE", "Fixe", false, TagColor.INFO));
    String body =
        "{\"code\":\"AUTRE_CODE\",\"label\":\"Fixe 2\",\"projectRequired\":false,\"color\":\"INFO\",\"version\":0}";
    mockMvc
        .perform(as(put("/api/v1/activity-types/" + t.getId()), admin, body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("FIXE"));
  }

  @Test
  @DisplayName("RG-02-07 : sans saisie (port par défaut), projectRequired reste modifiable")
  void updateWithDefaultCheckerAllowsChange() throws Exception {
    // SFD-01 absente : l'implémentation par défaut répond « non utilisé » ; le cas « utilisé »
    // est testé au niveau service (ActivityTypeServiceTest)
    assertThat(usageChecker.isUsed(1L)).isFalse();
    ActivityType t = types.saveAndFlush(new ActivityType("LIBRE", "Libre", false, TagColor.INFO));
    mockMvc
        .perform(
            as(put("/api/v1/activity-types/" + t.getId()), admin, updateBody("Libre", true, 0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectRequired").value(true));
  }

  @Test
  @DisplayName("Modification concurrente : une version périmée donne 409")
  void staleVersionIs409() throws Exception {
    ActivityType t = types.saveAndFlush(new ActivityType("VERSION_T", "V", false, TagColor.INFO));
    mockMvc
        .perform(as(put("/api/v1/activity-types/" + t.getId()), admin, updateBody("V2", false, 7)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  @Test
  @DisplayName("RG-02-08 : l'ADMIN lit un type par identifiant ; 404 pour un identifiant inconnu")
  void getAndNotFound() throws Exception {
    ActivityType t = types.findByCode("REUNION").orElseThrow();
    mockMvc
        .perform(as(get("/api/v1/activity-types/" + t.getId()), admin, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("REUNION"));
    mockMvc
        .perform(as(get("/api/v1/activity-types/999999"), admin, null))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(as(put("/api/v1/activity-types/999999"), admin, updateBody("x", false, 0)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-02-09 : USER et MANAGER reçoivent 403 sur création, lecture et modification")
  void nonAdminIs403() throws Exception {
    Long id = types.findByCode("REUNION").orElseThrow().getId();
    for (User u : new User[] {plainUser, manager}) {
      mockMvc
          .perform(as(post("/api/v1/activity-types"), u, createBody("INTERDIT")))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(as(get("/api/v1/activity-types/" + id), u, null))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(as(put("/api/v1/activity-types/" + id), u, updateBody("x", false, 0)))
          .andExpect(status().isForbidden());
    }
    assertThat(types.findByCode("INTERDIT")).isEmpty();
  }

  @Test
  @DisplayName("CA-02-11 : sans jeton, création, lecture et modification répondent 401")
  void unauthenticatedIs401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/activity-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("ANON")))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/activity-types/1")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put("/api/v1/activity-types/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("x", false, 0)))
        .andExpect(status().isUnauthorized());
  }
}
