package com.apptracer.common.time;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppTimeProperties.class)
public class TimeConfig {

  /** RG-00-01 : horloge sur le fuseau métier, injectée dans les services (figée dans les tests). */
  @Bean
  Clock clock(AppTimeProperties properties) {
    return Clock.system(properties.zone());
  }
}
