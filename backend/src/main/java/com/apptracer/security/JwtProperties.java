package com.apptracer.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Properties {@code app.security.jwt.*}, validated at start-up.
 *
 * @param secret HS256 key, required, at least 32 bytes (RG-03-06)
 * @param accessTtl lifetime of the access token, RG-03-06 (15 minutes by default)
 * @param refreshTtl lifetime of the refresh token, RG-03-07 (7 days by default)
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
