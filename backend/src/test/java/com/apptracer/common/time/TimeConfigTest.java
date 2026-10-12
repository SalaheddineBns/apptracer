package com.apptracer.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

class TimeConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @Import(TimeConfig.class)
  static class Config {}

  @Test
  @DisplayName("RG-00-01 : le Clock est sur Europe/Paris par défaut")
  void clockUsesParisByDefault() {
    runner.run(
        ctx -> assertThat(ctx.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Europe/Paris")));
  }
}
