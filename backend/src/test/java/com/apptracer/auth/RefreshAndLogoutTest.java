package com.apptracer.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshAndLogoutTest {

  private static final String PASSWORD = "Motdepasse12345";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder encoder;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JwtEncoder jwtEncoder;

  private User jean;

  @BeforeEach
  void setUp() {
    jean =
        users.save(
            new User("jean@example.com", "Jean", "Dupont", encoder.encode(PASSWORD), Role.USER));
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  private JsonNode loginTokens() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"jean@example.com\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return MAPPER.readTree(body);
  }

  private MvcResult call(String path, String refreshToken) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/" + path)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "fr")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
        .andReturn();
  }

  private String expiredAccessToken() {
    Instant past = Instant.now().minus(Duration.ofHours(1));
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(String.valueOf(jean.getId()))
            .claim("role", "USER")
            .issuedAt(past.minus(Duration.ofMinutes(15)))
            .expiresAt(past)
            .build();
    return jwtEncoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  @Test
  @DisplayName("CA-03-06 : access token expiré + refresh token valide, la session est renouvelée")
  void expiredAccessTokenIsRenewedWithRefreshToken() throws Exception {
    JsonNode first = loginTokens();
    String oldRefresh = first.get("refreshToken").asText();

    mockMvc
        .perform(
            get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken()))
        .andExpect(status().isUnauthorized());

    MvcResult refreshed = call("refresh", oldRefresh);
    assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
    JsonNode second = MAPPER.readTree(refreshed.getResponse().getContentAsString());
    assertThat(second.get("refreshToken").asText()).isNotEqualTo(oldRefresh);
    assertThat(second.get("user").get("email").asText()).isEqualTo("jean@example.com");

    // the new access token lets the action be replayed
    mockMvc
        .perform(
            get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + second.get("accessToken").asText()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(jean.getId()));

    // the old refresh token is invalidated, the new one is active, same session
    assertThat(call("refresh", oldRefresh).getResponse().getStatus()).isEqualTo(401);
    List<String> sessions =
        jdbc.queryForList("SELECT DISTINCT session_id::text FROM refresh_tokens", String.class);
    assertThat(sessions).hasSize(1);
  }

  @Test
  @DisplayName("CA-03-07 : un refresh token déjà utilisé, présenté de nouveau, donne 401")
  void reusedRefreshTokenReturns401() throws Exception {
    String refresh = loginTokens().get("refreshToken").asText();
    assertThat(call("refresh", refresh).getResponse().getStatus()).isEqualTo(200);

    MvcResult reused = call("refresh", refresh);
    MvcResult unknown = call("refresh", "jeton-inconnu");

    assertThat(reused.getResponse().getStatus()).isEqualTo(401);
    assertThat(reused.getResponse().getContentAsString()).contains("Identifiants invalides");
    // same response as for an unknown token
    assertThat(reused.getResponse().getContentAsString())
        .isEqualTo(unknown.getResponse().getContentAsString());
  }

  @Test
  @DisplayName("CA-03-07 : deux renouvellements simultanés avec le même jeton, un seul réussit")
  void concurrentRefreshOnlyOneWins() throws Exception {
    String refresh = loginTokens().get("refreshToken").asText();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Integer> task = () -> call("refresh", refresh).getResponse().getStatus();
      Future<Integer> a = pool.submit(task);
      Future<Integer> b = pool.submit(task);
      assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 401);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("CA-03-07 : un refresh token expiré est refusé (401)")
  void expiredRefreshTokenReturns401() throws Exception {
    String refresh = loginTokens().get("refreshToken").asText();
    jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 minute'");
    assertThat(call("refresh", refresh).getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("RG-03-05 : un compte inactif ne peut pas renouveler sa session (401)")
  void inactiveAccountCannotRefresh() throws Exception {
    String refresh = loginTokens().get("refreshToken").asText();
    jean.setActive(false);
    users.save(jean);

    MvcResult result = call("refresh", refresh);

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(result.getResponse().getContentAsString()).contains("Identifiants invalides");
  }

  @Test
  @DisplayName("CA-03-08 : après déconnexion, le refresh token est refusé (401)")
  void logoutRevokesRefreshToken() throws Exception {
    String refresh = loginTokens().get("refreshToken").asText();

    assertThat(call("logout", refresh).getResponse().getStatus()).isEqualTo(204);

    assertThat(call("refresh", refresh).getResponse().getStatus()).isEqualTo(401);
    assertThat(jdbc.queryForObject("SELECT revocation_reason FROM refresh_tokens", String.class))
        .isEqualTo("LOGOUT");
  }

  @Test
  @DisplayName("CA-03-08 : la déconnexion est idempotente (204 même pour un jeton inconnu)")
  void logoutIsIdempotent() throws Exception {
    assertThat(call("logout", "jeton-inconnu").getResponse().getStatus()).isEqualTo(204);
    String refresh = loginTokens().get("refreshToken").asText();
    assertThat(call("logout", refresh).getResponse().getStatus()).isEqualTo(204);
    assertThat(call("logout", refresh).getResponse().getStatus()).isEqualTo(204);
  }

  @Test
  @DisplayName("refreshSession / logout : jeton vide, 400")
  void blankTokenReturns400() throws Exception {
    for (String path : new String[] {"refresh", "logout"}) {
      mockMvc
          .perform(
              post("/api/v1/auth/" + path)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"refreshToken\":\"\"}"))
          .andExpect(status().isBadRequest());
    }
  }
}
