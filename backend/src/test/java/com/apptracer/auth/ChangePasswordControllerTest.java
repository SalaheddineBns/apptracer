package com.apptracer.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChangePasswordControllerTest {

  private static final String OLD = "Ancien-motdepasse-1";
  private static final String NEW = "Nouveau-motdepasse-2";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder encoder;
  @Autowired private JdbcTemplate jdbc;

  private User jean;

  @BeforeEach
  void setUp() {
    jean =
        users.save(new User("jean@example.com", "Jean", "Dupont", encoder.encode(OLD), Role.USER));
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  private JsonNode login(String email, String password) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return MAPPER.readTree(body);
  }

  private int loginStatus(String email, String password) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andReturn()
        .getResponse()
        .getStatus();
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

  private ResultActions change(String accessToken, String current, String next) throws Exception {
    var request =
        put("/api/v1/auth/me/password")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}");
    if (accessToken != null) {
      request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }
    return mockMvc.perform(request);
  }

  @Test
  @DisplayName("RG-03-09 : changeMyPassword sans access token, 401")
  void noTokenReturns401() throws Exception {
    change(null, OLD, NEW).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("RG-03-15 : tout rôle connecté peut changer son mot de passe (USER, MANAGER, ADMIN)")
  void everyRoleCanChangeOwnPassword() throws Exception {
    for (Role role : Role.values()) {
      String email = role.name().toLowerCase() + "@example.com";
      users.save(new User(email, "N", "P", encoder.encode(OLD), role));
      String access = login(email, OLD).get("accessToken").asText();
      change(access, OLD, NEW).andExpect(status().isNoContent());
      assertThat(loginStatus(email, NEW)).isEqualTo(200);
    }
  }

  @Test
  @DisplayName("RG-03-15 : le nouveau mot de passe permet de se connecter, l'ancien non")
  void newPasswordWorksOldDoesNot() throws Exception {
    String access = login("jean@example.com", OLD).get("accessToken").asText();

    change(access, OLD, NEW).andExpect(status().isNoContent());

    assertThat(loginStatus("jean@example.com", NEW)).isEqualTo(200);
    assertThat(loginStatus("jean@example.com", OLD)).isEqualTo(401);
    User after = users.findById(jean.getId()).orElseThrow();
    assertThat(after.getPasswordHash()).startsWith("$2").doesNotContain(NEW);
    assertThat(after.getUpdatedBy()).isEqualTo(jean.getId());
  }

  @Test
  @DisplayName(
      "CA-03-14 : ancien mot de passe incorrect, 409 RG-03-15 sur currentPassword, mot de passe inchangé")
  void wrongCurrentPasswordReturns409() throws Exception {
    String access = login("jean@example.com", OLD).get("accessToken").asText();
    String hash = users.findById(jean.getId()).orElseThrow().getPasswordHash();

    change(access, "Mauvais-motdepasse-9", NEW)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-03-15"))
        .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));

    assertThat(users.findById(jean.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
    assertThat(loginStatus("jean@example.com", OLD)).isEqualTo(200);
    assertThat(loginStatus("jean@example.com", NEW)).isEqualTo(401);
  }

  @Test
  @DisplayName("RG-03-02 : un nouveau mot de passe trop faible, 400 avec newPassword en erreur")
  void weakNewPasswordReturns400() throws Exception {
    String access = login("jean@example.com", OLD).get("accessToken").asText();
    String hash = users.findById(jean.getId()).orElseThrow().getPasswordHash();

    change(access, OLD, "abcd1234")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("newPassword"));

    assertThat(users.findById(jean.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
  }

  @Test
  @DisplayName(
      "RG-03-15 : les autres sessions sont révoquées, la session courante reste utilisable")
  void otherSessionsRevokedCurrentKept() throws Exception {
    JsonNode current = login("jean@example.com", OLD);
    JsonNode other = login("jean@example.com", OLD);

    change(current.get("accessToken").asText(), OLD, NEW).andExpect(status().isNoContent());

    assertThat(
            jdbc.queryForObject(
                "SELECT revocation_reason FROM refresh_tokens WHERE revoked_at IS NOT NULL",
                String.class))
        .isEqualTo("PASSWORD_CHANGED");
    assertThat(refresh(other.get("refreshToken").asText())).isEqualTo(401);
    assertThat(refresh(current.get("refreshToken").asText())).isEqualTo(200);
  }

  @Test
  @DisplayName("RG-03-15 : seul le compte de l'utilisateur connecté est concerné")
  void otherAccountsAreUntouched() throws Exception {
    User paul =
        users.save(new User("paul@example.com", "Paul", "P", encoder.encode(OLD), Role.USER));
    JsonNode paulSession = login("paul@example.com", OLD);
    String access = login("jean@example.com", OLD).get("accessToken").asText();

    change(access, OLD, NEW).andExpect(status().isNoContent());

    assertThat(refresh(paulSession.get("refreshToken").asText())).isEqualTo(200);
    assertThat(loginStatus("paul@example.com", OLD)).isEqualTo(200);
    assertThat(users.findById(paul.getId()).orElseThrow().getPasswordHash())
        .isNotEqualTo(users.findById(jean.getId()).orElseThrow().getPasswordHash());
  }
}
