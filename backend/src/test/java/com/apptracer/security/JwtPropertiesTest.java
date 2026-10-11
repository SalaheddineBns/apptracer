package com.apptracer.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

class JwtPropertiesTest {

  @Import(JwtConfig.class)
  static class Config {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @Test
  @DisplayName("RG-03-06 : sans JWT_SECRET le démarrage échoue")
  void missingSecretFailsStartup() {
    runner.run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  @DisplayName("RG-03-06 : un secret de moins de 32 octets fait échouer le démarrage")
  void shortSecretFailsStartup() {
    runner
        .withPropertyValues("app.security.jwt.secret=too-short")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  @DisplayName("RG-03-06 / RG-03-07 : durées par défaut 15 minutes et 7 jours")
  void defaultTtls() {
    runner
        .withPropertyValues("app.security.jwt.secret=0123456789abcdef0123456789abcdef")
        .run(
            ctx -> {
              JwtProperties p = ctx.getBean(JwtProperties.class);
              assertThat(p.accessTtl()).isEqualTo(Duration.ofMinutes(15));
              assertThat(p.refreshTtl()).isEqualTo(Duration.ofDays(7));
            });
  }
}
