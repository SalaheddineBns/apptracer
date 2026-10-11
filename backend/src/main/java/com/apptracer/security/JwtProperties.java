package com.apptracer.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Propriétés {@code app.security.jwt.*}, validées au démarrage.
 *
 * @param secret clé HS256, obligatoire, au moins 32 octets (RG-03-06)
 * @param accessTtl durée de vie de l'access token, RG-03-06 (15 minutes par défaut)
 * @param refreshTtl durée de vie du refresh token, RG-03-07 (7 jours par défaut)
 */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
    String secret,
    @NotNull @DefaultValue("15m") Duration accessTtl,
    @NotNull @DefaultValue("7d") Duration refreshTtl) {

  @AssertTrue(message = "JWT_SECRET est obligatoire et doit faire au moins 32 octets")
  boolean isSecretValid() {
    return secret != null && secret.getBytes(StandardCharsets.UTF_8).length >= 32;
  }

  @AssertTrue(message = "JWT_ACCESS_TTL et JWT_REFRESH_TTL doivent être strictement positives")
  boolean isTtlPositive() {
    return accessTtl != null
        && refreshTtl != null
        && accessTtl.isPositive()
        && refreshTtl.isPositive();
  }
}
