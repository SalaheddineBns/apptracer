package com.apptracer.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/** JWT HS256 (RG-03-06) et hiérarchie des rôles (RG-03-10). */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

  private static SecretKey key(JwtProperties properties) {
    return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(JwtProperties properties) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key(properties)));
  }

  @Bean
  JwtDecoder jwtDecoder(JwtProperties properties) {
    return NimbusJwtDecoder.withSecretKey(key(properties)).macAlgorithm(MacAlgorithm.HS256).build();
  }

  /**
   * RG-03-06 : le rôle vient du claim {@code role}. Le nom de l'authentification est le claim
   * {@code sub} (identifiant de l'utilisateur), lu par {@code SecurityAuditorAware}.
   */
  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          String role = jwt.getClaimAsString(AccessTokenService.CLAIM_ROLE);
          Collection<GrantedAuthority> authorities =
              role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
          return authorities;
        });
    return converter;
  }

  /** RG-03-10 : MANAGER inclut les droits USER ; ADMIN n'hérite de rien. */
  @Bean
  static RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy("ROLE_MANAGER > ROLE_USER");
  }
}
