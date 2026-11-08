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
import com.apptracer.referential.domain.JobTitle;
import com.apptracer.referential.repository.JobTitleRepository;
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
class JobTitleControllerTest {

  private static final String URL = "/api/v1/job-titles";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  private JobTitleRepository titles;

  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User admin;
  private User plainUser;
  private User manager;

  @BeforeEach
  void setUp() {
    admin = users.save(new User("jt-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    plainUser = users.save(new User("jt-user@example.com", "U", "User", "hash", Role.USER));
    manager = users.save(new User("jt-mgr@example.com", "M", "Mgr", "hash", Role.MANAGER));
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("UPDATE users SET job_title_id = NULL");
    jdbc.execute(
        "DELETE FROM job_titles WHERE code NOT IN"
            + " ('DEVELOPER','TECH_LEAD','QA','SCRUM_MASTER','PRODUCT_OWNER','DATA_ANALYST')");
    jdbc.execute(
        "UPDATE job_titles SET active = TRUE, version = 0, created_by = NULL, updated_by = NULL");
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

  private static String createBody(String code, String label) {
    return "{\"code\":\"" + code + "\",\"label\":\"" + label + "\"}";
  }

  private static String updateBody(String label, long version) {
    return "{\"label\":\"" + label + "\",\"version\":" + version + "}";
  }

  @Test
  @DisplayName(
      "CA-09-01 : une installation neuve donne six fonctions actives, triées par libellé, à tous")
  void listsSixInitialTitlesSorted() throws Exception {
    for (User u : new User[] {plainUser, manager, admin}) {
      mockMvc
          .perform(as(get(URL), u, null))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(6))
          .andExpect(jsonPath("$[?(@.active == false)]").isEmpty())
          .andExpect(jsonPath("$[0].label").value("Data Analyst"))
          .andExpect(jsonPath("$[1].label").value("Developer"))
          .andExpect(jsonPath("$[2].label").value("Product Owner"))
          .andExpect(jsonPath("$[3].label").value("QA"))
          .andExpect(jsonPath("$[4].label").value("Scrum Master"))
          .andExpect(jsonPath("$[5].label").value("Tech Lead")); // RG-09-08
    }
  }

  @Test
  @DisplayName(
      "CA-09-02 : l'ADMIN crée « UX Designer » (201, Location, traçabilité), elle est listée")
  void adminCreates() throws Exception {
    mockMvc
        .perform(as(post(URL), admin, createBody("UX_DESIGNER", "UX Designer")))
        .andExpect(status().isCreated())
        .andExpect(header().exists(HttpHeaders.LOCATION))
        .andExpect(jsonPath("$.code").value("UX_DESIGNER"))
        .andExpect(jsonPath("$.label").value("UX Designer"))
        .andExpect(jsonPath("$.active").value(true));
    JobTitle t = titles.findByCode("UX_DESIGNER").orElseThrow();
    assertThat(t.getCreatedBy()).isEqualTo(admin.getId()); // RG-09-11
    assertThat(t.getUpdatedBy()).isEqualTo(admin.getId());
    mockMvc
        .perform(as(get(URL), plainUser, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(7))
        .andExpect(jsonPath("$[?(@.code == 'UX_DESIGNER')].label").value("UX Designer"));
  }

  @Test
  @DisplayName(
      "CA-09-03 : TECH_LEAD ou tech_lead déjà existant donne 409 RG-09-05 sur le champ code")
  void duplicateCodeIs409IgnoringCase() throws Exception {
    for (String code : new String[] {"TECH_LEAD", "tech_lead", "Tech_Lead"}) {
      mockMvc
          .perform(as(post(URL), admin, createBody(code, "Autre")))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("RG-09-05"))
          .andExpect(jsonPath("$.errors[0].field").value("code"));
    }
    assertThat(titles.count()).isEqualTo(6);
  }

  @Test
  @DisplayName(
      "CA-09-03 : la violation de l'index unique en base (course) donne aussi 409 RG-09-05")
  void raceOnUniqueIndexIs409() throws Exception {
    // le contrôle de service ne voit pas le doublon : seul l'index unique le refuse
    org.mockito.Mockito.doReturn(false).when(titles).existsByCode("TECH_LEAD");
    mockMvc
        .perform(as(post(URL), admin, createBody("TECH_LEAD", "Autre")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-09-05"))
        .andExpect(jsonPath("$.errors[0].field").value("code"));
  }

  @Test
  @DisplayName("CA-09-03 : un code invalide (« tech lead ») donne 400 avec le champ en erreur")
  void invalidCodeIs400() throws Exception {
    for (String bad : new String[] {"tech lead", "é_é", "A", "A".repeat(31), "A-B"}) {
      mockMvc
          .perform(as(post(URL), admin, createBody(bad, "x")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("code"));
    }
    // RG-09-05 : un code libre mais en minuscules est refusé, jamais enregistré
    mockMvc
        .perform(as(post(URL), admin, createBody("ux_designer", "x")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("code"));
    assertThat(titles.count()).isEqualTo(6);
  }

  @Test
  @DisplayName(
      "RG-09-05 : le libellé est obligatoire et limité à 100 caractères ; doublon de libellé admis")
  void labelRules() throws Exception {
    mockMvc
        .perform(as(post(URL), admin, "{\"code\":\"SANS_LIBELLE\"}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(as(post(URL), admin, createBody("TROP_LONG", "x".repeat(101))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("label"));
    mockMvc
        .perform(as(post(URL), admin, createBody("AUTRE_QA", "QA")))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "RG-09-05 / RG-09-11 : l'ADMIN modifie le libellé, le code reste, updated_by est l'ADMIN")
  void adminUpdates() throws Exception {
    JobTitle t = titles.saveAndFlush(new JobTitle("A_MODIFIER", "Ancien"));
    String body = "{\"code\":\"AUTRE_CODE\",\"label\":\"Nouveau\",\"version\":0}";
    mockMvc
        .perform(as(put(URL + "/" + t.getId()), admin, body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("A_MODIFIER"))
        .andExpect(jsonPath("$.label").value("Nouveau"))
        .andExpect(jsonPath("$.version").value(1));
    assertThat(titles.findById(t.getId()).orElseThrow().getUpdatedBy()).isEqualTo(admin.getId());
  }

  @Test
  @DisplayName("Modification concurrente : une version périmée donne 409")
  void staleVersionIs409() throws Exception {
    JobTitle t = titles.saveAndFlush(new JobTitle("VERSION_T", "V"));
    mockMvc
        .perform(as(put(URL + "/" + t.getId()), admin, updateBody("V2", 7)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  @Test
  @DisplayName(
      "RG-09-07 : l'ADMIN lit une fonction par identifiant ; 404 pour un identifiant inconnu")
  void getAndNotFound() throws Exception {
    Long id = titles.findByCode("QA").orElseThrow().getId();
    mockMvc
        .perform(as(get(URL + "/" + id), admin, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("QA"));
    mockMvc.perform(as(get(URL + "/999999"), admin, null)).andExpect(status().isNotFound());
    mockMvc
        .perform(as(put(URL + "/999999"), admin, updateBody("x", 0)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(as(post(URL + "/999999/deactivation"), admin, null))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "CA-09-09 : USER et MANAGER reçoivent 403 sur création, lecture par id, modification, désactivation, réactivation")
  void nonAdminIs403() throws Exception {
    Long id = titles.findByCode("QA").orElseThrow().getId();
    for (User u : new User[] {plainUser, manager}) {
      mockMvc
          .perform(as(post(URL), u, createBody("INTERDIT", "x")))
          .andExpect(status().isForbidden());
      mockMvc.perform(as(get(URL + "/" + id), u, null)).andExpect(status().isForbidden());
      mockMvc
          .perform(as(put(URL + "/" + id), u, updateBody("x", 0)))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(as(post(URL + "/" + id + "/deactivation"), u, null))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(as(post(URL + "/" + id + "/reactivation"), u, null))
          .andExpect(status().isForbidden());
    }
    assertThat(titles.findByCode("INTERDIT")).isEmpty();
    assertThat(titles.findByCode("QA").orElseThrow().isActive()).isTrue();
  }

  @Test
  @DisplayName("CA-09-09 : sans jeton, toutes les opérations répondent 401")
  void unauthenticatedIs401() throws Exception {
    mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(URL + "/1")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(createBody("ANON", "x")))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put(URL + "/1").contentType(MediaType.APPLICATION_JSON).content(updateBody("x", 0)))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post(URL + "/1/deactivation")).andExpect(status().isUnauthorized());
    mockMvc.perform(post(URL + "/1/reactivation")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "RG-09-06 / RG-09-07 : une fonction désactivée disparaît pour USER et MANAGER, reste visible inactive pour l'ADMIN, puis revient réactivée")
  void deactivationLifecycle() throws Exception {
    JobTitle t = titles.findByCode("QA").orElseThrow();
    String url = URL + "/" + t.getId();

    mockMvc
        .perform(as(post(url + "/deactivation"), admin, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.code").value("QA"));
    assertThat(titles.findById(t.getId()).orElseThrow().getUpdatedBy()).isEqualTo(admin.getId());

    for (User u : new User[] {plainUser, manager}) {
      mockMvc
          .perform(as(get(URL), u, null))
          .andExpect(jsonPath("$.length()").value(5))
          .andExpect(jsonPath("$[?(@.code == 'QA')]").isEmpty());
    }
    mockMvc
        .perform(as(get(URL), admin, null))
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[?(@.code == 'QA')].active").value(false));

    mockMvc
        .perform(as(post(url + "/reactivation"), admin, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));
    mockMvc
        .perform(as(get(URL), plainUser, null))
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[?(@.code == 'QA')].active").value(true));
  }

  @Test
  @DisplayName("RG-09-06 : désactivation et réactivation redondantes répondent 200 sans effet")
  void redundantToggleIs200() throws Exception {
    Long id = titles.findByCode("QA").orElseThrow().getId();
    mockMvc
        .perform(as(post(URL + "/" + id + "/reactivation"), admin, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(0));
    mockMvc.perform(as(post(URL + "/" + id + "/deactivation"), admin, null));
    mockMvc
        .perform(as(post(URL + "/" + id + "/deactivation"), admin, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  @DisplayName("RG-09-06 : aucun endpoint de suppression n'est exposé")
  void noDeleteEndpoint() throws Exception {
    Long id = titles.findByCode("QA").orElseThrow().getId();
    mockMvc.perform(as(delete(URL + "/" + id), admin, null)).andExpect(status().is4xxClientError());
    assertThat(titles.findByCode("QA")).isPresent();
  }
}
