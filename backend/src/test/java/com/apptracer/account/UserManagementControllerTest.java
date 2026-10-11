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
import com.apptracer.security.AccessTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
class UserManagementControllerTest {

  private static final String PASSWORD = "Motdepasse12345";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder encoder;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AccessTokenService tokens;

  private User admin;
  private User jean;
  private User plain;

  @BeforeEach
  void setUp() {
    admin =
        users.save(
            new User("admin@example.com", "Ada", "Admin", encoder.encode(PASSWORD), Role.ADMIN));
    jean =
        users.save(
            new User("jean@example.com", "Jean", "Dupont", encoder.encode(PASSWORD), Role.USER));
    plain =
        users.save(
            new User("plain@example.com", "Paul", "Plain", encoder.encode(PASSWORD), Role.USER));
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  private String bearer(User user) {
    return "Bearer " + tokens.issue(user.getId(), user.getRole(), UUID.randomUUID());
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, User actor) {
    return actor == null ? b : b.header(HttpHeaders.AUTHORIZATION, bearer(actor));
  }

  private static String body(String first, String last, String role, long version) {
    return "{\"firstName\":\"%s\",\"lastName\":\"%s\",\"role\":\"%s\",\"version\":%d}"
        .formatted(first, last, role, version);
  }

  private MockHttpServletRequestBuilder update(User actor, Long id, String json) {
    return as(
        put("/api/v1/users/" + id).contentType(MediaType.APPLICATION_JSON).content(json), actor);
  }

  private String loginRefreshToken(String email) throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new ObjectMapper().readTree(json).get("refreshToken").asText();
  }

  private int refresh(String refreshToken) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private int activeTokens(Long userId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
        Integer.class,
        userId);
  }

  @Test
  @DisplayName(
      "CA-03-09 : updateUser, deactivateUser et reactivateUser répondent 403 à un USER ou un MANAGER")
  void nonAdminGets403() throws Exception {
    User manager =
        users.save(new User("m@example.com", "M", "M", encoder.encode(PASSWORD), Role.MANAGER));
    for (User actor : List.of(plain, manager)) {
      mockMvc
          .perform(update(actor, jean.getId(), body("A", "B", "USER", 0)))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(as(post("/api/v1/users/" + jean.getId() + "/deactivation"), actor))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(as(post("/api/v1/users/" + jean.getId() + "/reactivation"), actor))
          .andExpect(status().isForbidden());
    }
    User unchanged = users.findById(jean.getId()).orElseThrow();
    assertThat(unchanged.getFirstName()).isEqualTo("Jean");
    assertThat(unchanged.isActive()).isTrue();
  }

  @Test
  @DisplayName("RG-03-09 : sans access token, les trois endpoints de modification répondent 401")
  void noTokenGets401() throws Exception {
    mockMvc
        .perform(update(null, jean.getId(), body("A", "B", "USER", 0)))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/api/v1/users/" + jean.getId() + "/deactivation"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/api/v1/users/" + jean.getId() + "/reactivation"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "RG-03-11 : un ADMIN modifie prénom et nom ; e-mail et mot de passe du corps sont ignorés")
  void adminUpdatesNamesOnly() throws Exception {
    String hash = jean.getPasswordHash();
    String json =
        "{\"firstName\":\"Jeanne\",\"lastName\":\"Martin\",\"role\":\"USER\",\"version\":0,"
            + "\"email\":\"hack@example.com\",\"password\":\"Autre-mot-de-passe-1\"}";

    mockMvc
        .perform(update(admin, jean.getId(), json))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Jeanne"))
        .andExpect(jsonPath("$.lastName").value("Martin"))
        .andExpect(jsonPath("$.email").value("jean@example.com"))
        .andExpect(jsonPath("$.version").value(1));

    User after = users.findById(jean.getId()).orElseThrow();
    assertThat(after.getEmail()).isEqualTo("jean@example.com");
    assertThat(after.getPasswordHash()).isEqualTo(hash);
  }

  @Test
  @DisplayName(
      "RG-00-04 : updated_by porte l'identifiant de l'ADMIN qui modifie, created_by est conservé")
  void updateIsTraced() throws Exception {
    mockMvc
        .perform(update(admin, jean.getId(), body("Jeanne", "Martin", "USER", 0)))
        .andExpect(status().isOk());

    User after = users.findById(jean.getId()).orElseThrow();
    assertThat(after.getUpdatedBy()).isEqualTo(admin.getId());
    assertThat(after.getCreatedBy()).isNull();
    assertThat(after.getUpdatedAt()).isAfterOrEqualTo(after.getCreatedAt());
  }

  @Test
  @DisplayName(
      "Conflit de version : une version périmée donne 409 concurrent-modification sans code")
  void staleVersionReturns409() throws Exception {
    mockMvc
        .perform(update(admin, jean.getId(), body("A", "B", "USER", 0)))
        .andExpect(status().isOk());

    mockMvc
        .perform(update(admin, jean.getId(), body("C", "D", "USER", 0)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("https://apptracer/errors/concurrent-modification"))
        .andExpect(jsonPath("$.code").doesNotExist());
    assertThat(users.findById(jean.getId()).orElseThrow().getFirstName()).isEqualTo("A");
  }

  @Test
  @DisplayName("RG-03-10 : un ADMIN change le rôle d'un compte")
  void adminChangesRole() throws Exception {
    mockMvc
        .perform(update(admin, jean.getId(), body("Jean", "Dupont", "MANAGER", 0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("MANAGER"));
  }

  @Test
  @DisplayName("RG-03-11 : updateUser, champs invalides 400 et compte inconnu 404")
  void validationAndNotFound() throws Exception {
    mockMvc
        .perform(
            update(
                admin, jean.getId(), "{\"firstName\":\"\",\"lastName\":\"B\",\"role\":\"USER\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").isNotEmpty());
    mockMvc
        .perform(update(admin, 999999L, body("A", "B", "USER", 0)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(as(post("/api/v1/users/999999/deactivation"), admin))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(as(post("/api/v1/users/999999/reactivation"), admin))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-03-12 : un ADMIN qui tente de se désactiver lui-même reçoit 409 RG-03-13")
  void adminCannotDeactivateSelf() throws Exception {
    mockMvc
        .perform(as(post("/api/v1/users/" + admin.getId() + "/deactivation"), admin))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-03-13"));
    assertThat(users.findById(admin.getId()).orElseThrow().isActive()).isTrue();
  }

  @Test
  @DisplayName("CA-03-12 : un ADMIN qui retire son propre rôle ADMIN reçoit 409 RG-03-13")
  void adminCannotRemoveOwnRole() throws Exception {
    mockMvc
        .perform(update(admin, admin.getId(), body("Ada", "Admin", "USER", 0)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-03-13"))
        .andExpect(jsonPath("$.errors[0].field").value("role"));
    assertThat(users.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
  }

  @Test
  @DisplayName(
      "CA-03-13 : après désactivation, le renouvellement de session échoue (401), l'access token reste valable")
  void deactivatedAccountCannotRefresh() throws Exception {
    String refresh = loginRefreshToken("jean@example.com");
    String accessToken = bearer(jean);
    assertThat(activeTokens(jean.getId())).isEqualTo(1);

    mockMvc
        .perform(as(post("/api/v1/users/" + jean.getId() + "/deactivation"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    assertThat(refresh(refresh)).isEqualTo(401);
    assertThat(activeTokens(jean.getId())).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT revocation_reason FROM refresh_tokens WHERE user_id = ?",
                String.class,
                jean.getId()))
        .isEqualTo("ACCOUNT_DEACTIVATED");
    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, accessToken))
        .andExpect(status().isOk());
    assertThat(users.findById(jean.getId()).orElseThrow().getUpdatedBy()).isEqualTo(admin.getId());
  }

  @Test
  @DisplayName("RG-03-14 : seuls les refresh tokens du compte désactivé sont révoqués")
  void deactivationOnlyRevokesTargetAccountTokens() throws Exception {
    loginRefreshToken("jean@example.com");
    loginRefreshToken("plain@example.com");

    mockMvc
        .perform(as(post("/api/v1/users/" + jean.getId() + "/deactivation"), admin))
        .andExpect(status().isOk());

    assertThat(activeTokens(jean.getId())).isZero();
    assertThat(activeTokens(plain.getId())).isEqualTo(1);
  }

  @Test
  @DisplayName("RG-03-14 : un changement de rôle révoque tous les refresh tokens du compte")
  void roleChangeRevokesTokens() throws Exception {
    String refresh = loginRefreshToken("jean@example.com");
    loginRefreshToken("jean@example.com");

    mockMvc
        .perform(update(admin, jean.getId(), body("Jean", "Dupont", "MANAGER", 0)))
        .andExpect(status().isOk());

    assertThat(activeTokens(jean.getId())).isZero();
    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT revocation_reason FROM refresh_tokens WHERE user_id = ?",
                String.class,
                jean.getId()))
        .containsExactly("ROLE_CHANGED");
    assertThat(refresh(refresh)).isEqualTo(401);
  }

  @Test
  @DisplayName("RG-03-14 : une modification sans changement de rôle ne révoque aucun refresh token")
  void nameChangeKeepsTokens() throws Exception {
    String refresh = loginRefreshToken("jean@example.com");

    mockMvc
        .perform(update(admin, jean.getId(), body("Jeanne", "Dupont", "USER", 0)))
        .andExpect(status().isOk());

    assertThat(activeTokens(jean.getId())).isEqualTo(1);
    assertThat(refresh(refresh)).isEqualTo(200);
  }

  @Test
  @DisplayName(
      "RG-03-05 : un compte désactivé ne peut plus se connecter, réactivé il le peut de nouveau")
  void reactivationRestoresLogin() throws Exception {
    mockMvc
        .perform(as(post("/api/v1/users/" + jean.getId() + "/deactivation"), admin))
        .andExpect(status().isOk());
    String loginJson = "{\"email\":\"jean@example.com\",\"password\":\"" + PASSWORD + "\"}";
    mockMvc
        .perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(as(post("/api/v1/users/" + jean.getId() + "/reactivation"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));

    mockMvc
        .perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson))
        .andExpect(status().isOk());
    assertThat(users.findById(jean.getId()).orElseThrow().getUpdatedBy()).isEqualTo(admin.getId());
  }

  @Test
  @DisplayName("Désactivation / réactivation répétées : sans effet, 200")
  void repeatedStateChangesAreNoOps() throws Exception {
    mockMvc
        .perform(as(post("/api/v1/users/" + jean.getId() + "/reactivation"), admin))
        .andExpect(status().isOk());
    mockMvc
        .perform(as(post("/api/v1/users/" + jean.getId() + "/deactivation"), admin))
        .andExpect(status().isOk());
    mockMvc
        .perform(as(post("/api/v1/users/" + jean.getId() + "/deactivation"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }
}
