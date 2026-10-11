package com.apptracer.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.common.audit.SecurityAuditorAware;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, JwtSecurityTest.JwtProbeConfig.class})
class JwtSecurityTest {

  @RestController
  static class JwtProbeController {
    @GetMapping("/api/v1/jwtprobe/user")
    @PreAuthorize("hasRole('USER')")
    void user() {}

    @GetMapping("/api/v1/jwtprobe/manager")
    @PreAuthorize("hasRole('MANAGER')")
    void manager() {}

    @GetMapping("/api/v1/jwtprobe/auditor")
    String auditor() {
      return new SecurityAuditorAware().getCurrentAuditor().map(String::valueOf).orElse("none");
    }
  }

  @TestConfiguration
  static class JwtProbeConfig {
    @Bean
    JwtProbeController jwtProbeController() {
      return new JwtProbeController();
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenService accessTokenService;
  @Autowired private JwtDecoder jwtDecoder;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private JwtProperties properties;

  private String bearer(Role role) {
    return "Bearer " + accessTokenService.issue(7L, role, UUID.randomUUID());
  }

  @Test
  @DisplayName("RG-03-06 : l'access token porte sub, role, sid et expire après JWT_ACCESS_TTL")
  void accessTokenCarriesClaims() {
    UUID sid = UUID.randomUUID();
    Jwt jwt = jwtDecoder.decode(accessTokenService.issue(42L, Role.MANAGER, sid));
    assertThat(jwt.getSubject()).isEqualTo("42");
    assertThat(jwt.getClaimAsString("role")).isEqualTo("MANAGER");
    assertThat(jwt.getClaimAsString("sid")).isEqualTo(sid.toString());
    assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
        .isEqualTo(Duration.ofMinutes(15));
    assertThat(properties.accessTtl()).isEqualTo(Duration.ofMinutes(15));
    assertThat(properties.refreshTtl()).isEqualTo(Duration.ofDays(7));
  }

  @Test
  @DisplayName("RG-03-09 : sans access token, 401 ProblemDetail")
  void noTokenReturns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/jwtprobe/user"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("RG-03-09"));
  }

  @Test
  @DisplayName("RG-03-09 : access token expiré, 401 ProblemDetail")
  void expiredTokenReturns401() throws Exception {
    Instant past = Instant.now().minus(Duration.ofHours(1));
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject("7")
            .claim("role", "USER")
            .issuedAt(past.minus(Duration.ofMinutes(15)))
            .expiresAt(past)
            .build();
    String token =
        jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    mockMvc
        .perform(get("/api/v1/jwtprobe/user").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("RG-03-09"));
  }

  @Test
  @DisplayName("RG-03-09 : access token signé avec une autre clé, 401 ProblemDetail")
  void badSignatureReturns401() throws Exception {
    JwtEncoder other =
        new NimbusJwtEncoder(
            new ImmutableSecret<>(
                new SecretKeySpec(
                    "another-secret-another-secret-0123456789".getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256")));
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject("7")
            .claim("role", "ADMIN")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(600))
            .build();
    String token =
        other
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    mockMvc
        .perform(get("/api/v1/jwtprobe/user").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("RG-03-09"));
  }

  @Test
  @DisplayName("RG-03-09 : un USER est refusé (403) sur une ressource MANAGER")
  void insufficientRoleReturns403() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/jwtprobe/manager").header(HttpHeaders.AUTHORIZATION, bearer(Role.USER)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("RG-03-09"));
  }

  @Test
  @DisplayName("RG-03-10 : MANAGER inclut les droits USER")
  void managerInheritsUser() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/jwtprobe/user").header(HttpHeaders.AUTHORIZATION, bearer(Role.MANAGER)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/jwtprobe/user").header(HttpHeaders.AUTHORIZATION, bearer(Role.USER)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("RG-03-10 : ADMIN n'hérite pas des droits USER")
  void adminDoesNotInheritUser() throws Exception {
    mockMvc
        .perform(get("/api/v1/jwtprobe/user").header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("RG-00-04 : avec un JWT, l'auditeur est le claim sub (identifiant numérique)")
  void auditorIsJwtSubject() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/jwtprobe/auditor").header(HttpHeaders.AUTHORIZATION, bearer(Role.USER)))
        .andExpect(status().isOk())
        .andExpect(content().string("7"));
  }

  @Test
  @DisplayName("RG-03-07 : les chemins d'authentification sont publics (pas de 401)")
  void authPathsArePublic() throws Exception {
    for (String path : new String[] {"login", "refresh", "logout"}) {
      int code = mockMvc.perform(get("/api/v1/auth/" + path)).andReturn().getResponse().getStatus();
      assertThat(code).isNotEqualTo(401);
    }
  }
}
