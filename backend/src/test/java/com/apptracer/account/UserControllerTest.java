package com.apptracer.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.security.AccessTokenService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserControllerTest {

  private static final String PASSWORD = "Motdepasse12345";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder encoder;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AccessTokenService tokens;

  private User admin;
  private User plainUser;
  private User manager;

  @BeforeEach
  void setUp() {
    admin =
        users.save(
            new User("admin@example.com", "Ada", "Admin", encoder.encode(PASSWORD), Role.ADMIN));
    plainUser =
        users.save(
            new User("user@example.com", "Ulysse", "User", encoder.encode(PASSWORD), Role.USER));
    manager =
        users.save(
            new User(
                "manager@example.com", "Marie", "Manager", encoder.encode(PASSWORD), Role.MANAGER));
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  private String bearer(User user) {
    return "Bearer " + tokens.issue(user.getId(), user.getRole(), UUID.randomUUID());
  }

  private static String json(String email, String password) {
    return "{\"email\":\""
        + email
        + "\",\"firstName\":\"Jean\",\"lastName\":\"Dupont\",\"role\":\"USER\",\"initialPassword\":\""
        + password
        + "\"}";
  }

  private MockHttpServletRequestBuilder createRequest(User actor, String body) {
    MockHttpServletRequestBuilder builder =
        post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body);
    return actor == null ? builder : builder.header(HttpHeaders.AUTHORIZATION, bearer(actor));
  }

  private MockHttpServletRequestBuilder withAuth(
      MockHttpServletRequestBuilder builder, User actor) {
    return actor == null ? builder : builder.header(HttpHeaders.AUTHORIZATION, bearer(actor));
  }

  @Test
  @DisplayName("CA-03-09 : un USER ou un MANAGER appelant l'API des comptes reçoit 403")
  void nonAdminGets403() throws Exception {
    for (User actor : List.of(plainUser, manager)) {
      mockMvc.perform(withAuth(get("/api/v1/users"), actor)).andExpect(status().isForbidden());
      mockMvc
          .perform(withAuth(get("/api/v1/users/" + admin.getId()), actor))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(createRequest(actor, json("new@example.com", PASSWORD)))
          .andExpect(status().isForbidden());
    }
    assertThat(users.count()).isEqualTo(3);
  }

  @Test
  @DisplayName("RG-03-09 : sans access token, les trois endpoints de comptes répondent 401")
  void noTokenGets401() throws Exception {
    mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/users/1")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(createRequest(null, json("new@example.com", PASSWORD)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "RG-03-11 : un ADMIN crée un compte (201, Location) avec le mot de passe initial défini")
  void adminCreatesAccount() throws Exception {
    MvcResult result =
        mockMvc
            .perform(createRequest(admin, json("jean@example.com", PASSWORD)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("jean@example.com"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn();

    User created = users.findByEmail("jean@example.com").orElseThrow();
    assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
        .endsWith("/api/v1/users/" + created.getId());
    // RG-03-12 / RG-03-03 : mot de passe initial haché, utilisable pour se connecter
    assertThat(created.getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"jean@example.com\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("RG-00-04 : created_by et updated_by portent l'identifiant de l'ADMIN qui crée")
  void creationIsTraced() throws Exception {
    mockMvc
        .perform(createRequest(admin, json("jean@example.com", PASSWORD)))
        .andExpect(status().isCreated());

    User created = users.findByEmail("jean@example.com").orElseThrow();
    assertThat(created.getCreatedBy()).isEqualTo(admin.getId());
    assertThat(created.getUpdatedBy()).isEqualTo(admin.getId());
    assertThat(created.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("CA-03-10 : e-mail déjà utilisé avec une autre casse, 409 RG-03-01")
  void duplicateEmailDifferentCaseReturns409() throws Exception {
    mockMvc
        .perform(createRequest(admin, json("USER@EXAMPLE.COM", PASSWORD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-03-01"))
        .andExpect(jsonPath("$.errors[0].field").value("email"));
    assertThat(users.count()).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "CA-03-10 : deux créations simultanées du même e-mail, un 201 et un 409 RG-03-01 (jamais 500)")
  void concurrentDuplicateCreationReturns409() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Integer> task =
          () ->
              mockMvc
                  .perform(createRequest(admin, json("race@example.com", PASSWORD)))
                  .andReturn()
                  .getResponse()
                  .getStatus();
      Future<Integer> a = pool.submit(task);
      Future<Integer> b = pool.submit(task);
      assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(201, 409);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName(
      "CA-03-11 : mot de passe de 8 caractères, 400 avec le champ initialPassword en erreur")
  void shortPasswordReturns400() throws Exception {
    mockMvc
        .perform(createRequest(admin, json("jean@example.com", "abcd1234")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("initialPassword"));
    assertThat(users.findByEmail("jean@example.com")).isEmpty();
  }

  @Test
  @DisplayName("RG-03-01 : e-mail au format invalide ou champs manquants, 400 avec errors[]")
  void invalidFormatReturns400() throws Exception {
    mockMvc
        .perform(createRequest(admin, json("pas-un-email", PASSWORD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("email"));
    mockMvc
        .perform(createRequest(admin, "{\"email\":\"a@b.fr\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").isNotEmpty());
  }

  @Test
  @DisplayName(
      "CA-03-15 : aucune réponse (création, lecture, liste) ne contient mot de passe ni haché")
  void noResponseContainsPasswordOrHash() throws Exception {
    String created =
        mockMvc
            .perform(createRequest(admin, json("jean@example.com", PASSWORD)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    User jean = users.findByEmail("jean@example.com").orElseThrow();
    String one =
        mockMvc
            .perform(withAuth(get("/api/v1/users/" + jean.getId()), admin))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String list =
        mockMvc
            .perform(withAuth(get("/api/v1/users"), admin))
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<String> hashes = users.findAll().stream().map(User::getPasswordHash).toList();
    for (String body : List.of(created, one, list)) {
      assertThat(body.toLowerCase()).doesNotContain("password").doesNotContain("hash");
      assertThat(body).doesNotContain(PASSWORD);
      hashes.forEach(h -> assertThat(body).doesNotContain(h));
    }
  }

  @Test
  @DisplayName("RG-03-11 : listUsers est paginé et trié par nom puis prénom")
  void listIsPagedAndSorted() throws Exception {
    mockMvc
        .perform(withAuth(get("/api/v1/users").param("size", "2"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].lastName").value("Admin"))
        .andExpect(jsonPath("$.content[1].lastName").value("Manager"));
    mockMvc
        .perform(withAuth(get("/api/v1/users").param("size", "2").param("page", "1"), admin))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].lastName").value("User"));
  }

  @Test
  @DisplayName("listUsers : tri sur le haché du mot de passe refusé (400)")
  void sortByHashIsRejected() throws Exception {
    mockMvc
        .perform(withAuth(get("/api/v1/users").param("sort", "passwordHash"), admin))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("sort"));
  }

  @Test
  @DisplayName("RG-03-11 : getUser renvoie le compte, 404 s'il n'existe pas")
  void getUserAndNotFound() throws Exception {
    mockMvc
        .perform(withAuth(get("/api/v1/users/" + plainUser.getId()), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.version").value(0));
    mockMvc
        .perform(withAuth(get("/api/v1/users/999999"), admin))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").doesNotExist());
  }
}
