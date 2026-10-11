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
        ctx -> {
          assertThat(ctx.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Europe/Paris"));
          assertThat(ctx.getBean(AppTimeProperties.class).standardDayMinutes()).isEqualTo(420);
        });
  }

  @Test
  @DisplayName("RG-00-02 : une journée standard non positive fait échouer le démarrage")
  void invalidStandardDayFailsStartup() {
    runner
        .withPropertyValues("app.time.standard-day-minutes=0")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  @DisplayName("RG-00-02 : la journée standard est configurable")
  void standardDayIsConfigurable() {
    runner
        .withPropertyValues("app.time.standard-day-minutes=450")
        .run(
            ctx ->
                assertThat(ctx.getBean(AppTimeProperties.class).standardDayMinutes())
                    .isEqualTo(450));
  }
}
