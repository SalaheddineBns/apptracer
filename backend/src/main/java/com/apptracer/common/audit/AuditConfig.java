package com.apptracer.common.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** RG-00-04 : active l'audit JPA ; les dates viennent du {@link Clock} applicatif. */
@Configuration
@EnableJpaAuditing(
    auditorAwareRef = "auditorAware",
    dateTimeProviderRef = "auditingDateTimeProvider")
public class AuditConfig {

  @Bean
  AuditorAware<Long> auditorAware() {
    return new SecurityAuditorAware();
  }

  @Bean
  DateTimeProvider auditingDateTimeProvider(Clock clock) {
    return () -> Optional.of(Instant.now(clock));
  }
}
