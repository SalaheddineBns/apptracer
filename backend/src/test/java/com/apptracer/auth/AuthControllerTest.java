package com.apptracer.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.security.AccessTokenService;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerTest {

  private static final String PASSWORD = "Motdepasse12345";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder encoder;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JwtDecoder jwtDecoder;
  @Autowired private AccessTokenService accessTokenService;

  private User jean;

  @BeforeEach
  void setUp() {
    jean =
        users.save(
            new User("jean@example.com", "Jean", "Dupont", encoder.encode(PASSWORD), Role.MANAGER));
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  private MvcResult login(String email, String password) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/login")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "fr")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andReturn();
  }

  @Test
  @DisplayName("CA-03-01 : un compte actif avec des identifiants corrects reçoit les deux jetons")
  void validLoginReturnsTokens() throws Exception {
    MvcResult result = login("Jean@Example.com", PASSWORD);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String body = result.getResponse().getContentAsString();
    com.fasterxml.jackson.databind.JsonNode json =
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
    Jwt jwt = jwtDecoder.decode(json.get("accessToken").asText());
    assertThat(jwt.getSubject()).isEqualTo(String.valueOf(jean.getId()));
    assertThat(jwt.getClaimAsString("role")).isEqualTo("MANAGER");
    assertThat(json.get("user").get("email").asText()).isEqualTo("jean@example.com");
    assertThat(json.get("user").get("role").asText()).isEqualTo("MANAGER");

    String refreshToken = json.get("refreshToken").asText();
    assertThat(refreshToken).isNotBlank();
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT token_hash FROM refresh_tokens");
    assertThat(rows).hasSize(1);
    String storedHash = ((String) rows.get(0).get("token_hash")).trim();
    assertThat(storedHash).hasSize(64).isNotEqualTo(refreshToken);
  }

  @Test
  @DisplayName("CA-03-02 : mot de passe incorrect, 401 « Identifiants invalides »")
  void wrongPasswordReturns401() throws Exception {
    MvcResult result = login("jean@example.com", "mauvais-mot-de-passe-1");

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(result.getResponse().getContentAsString())
        .contains("\"detail\":\"Identifiants invalides\"")
        .contains("\"code\":\"RG-03-04\"");
  }

  @Test
  @DisplayName("CA-03-03 : e-mail inconnu, réponse strictement identique à CA-03-02")
  void unknownEmailIsIdenticalToWrongPassword() throws Exception {
    MvcResult wrongPassword = login("jean@example.com", "mauvais-mot-de-passe-1");
    MvcResult unknownEmail = login("inconnu@example.com", "mauvais-mot-de-passe-1");

    assertThat(unknownEmail.getResponse().getStatus())
        .isEqualTo(wrongPassword.getResponse().getStatus())
        .isEqualTo(401);
    assertThat(unknownEmail.getResponse().getContentAsString())
        .isEqualTo(wrongPassword.getResponse().getContentAsString());
  }

  @Test
  @DisplayName("CA-03-04 : compte inactif avec le bon mot de passe, 401 « Identifiants invalides »")
  void inactiveAccountReturns401() throws Exception {
    MvcResult wrongPassword = login("jean@example.com", "mauvais-mot-de-passe-1");
    jean.setActive(false);
    users.save(jean);

    MvcResult result = login("jean@example.com", PASSWORD);

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(result.getResponse().getContentAsString())
        .isEqualTo(wrongPassword.getResponse().getContentAsString())
        .contains("Identifiants invalides");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isZero();
  }

  @Test
  @DisplayName(
      "CA-03-15 : les réponses de login et getCurrentUser ne contiennent ni mot de passe ni haché")
  void responsesNeverContainPasswordOrHash() throws Exception {
    String hash = users.findById(jean.getId()).orElseThrow().getPasswordHash();
    String loginBody = login("jean@example.com", PASSWORD).getResponse().getContentAsString();
    String meBody =
        mockMvc
            .perform(
                get("/api/v1/auth/me")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer "
                            + accessTokenService.issue(
                                jean.getId(), Role.MANAGER, UUID.randomUUID())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    for (String body : List.of(loginBody, meBody)) {
      assertThat(body).doesNotContain(hash).doesNotContain(PASSWORD);
      assertThat(body.toLowerCase()).doesNotContain("password");
    }
  }

  @Test
  @DisplayName("getCurrentUser : renvoie le compte du token")
  void getCurrentUserReturnsAccount() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/auth/me")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer "
                        + accessTokenService.issue(jean.getId(), Role.MANAGER, UUID.randomUUID())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(jean.getId()))
        .andExpect(jsonPath("$.firstName").value("Jean"))
        .andExpect(jsonPath("$.lastName").value("Dupont"))
        .andExpect(jsonPath("$.role").value("MANAGER"));
  }

  @Test
  @DisplayName("RG-03-09 : getCurrentUser sans token, 401")
  void getCurrentUserWithoutTokenReturns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("RG-03-09"));
  }

  @Test
  @DisplayName("login : champs vides, 400 avec errors[]")
  void blankFieldsReturn400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"password\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").isNotEmpty());
  }
}
