package com.apptracer.common.time;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppTimeProperties.class)
public class TimeConfig {

  /** RG-00-01: clock on the business time zone, injected into the services (fixed in the tests). */
  @Bean
  Clock clock(AppTimeProperties properties) {
    return Clock.system(properties.zone());
  }
}
