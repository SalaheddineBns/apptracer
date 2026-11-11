package com.apptracer.common.i18n;

import java.nio.charset.StandardCharsets;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * RG-08-06: server messages in French and English ({@code messages_fr.properties}, {@code
 * messages_en.properties}, UTF-8). No {@code messages.properties}: Spring Boot therefore does not
 * configure the {@code MessageSource} itself. Never a fallback to the system language: English is
 * the default language, set by {@link RequestLocaleResolver}.
 */
@Configuration
public class MessageSourceConfig {

  @Bean
  public MessageSource messageSource() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("messages");
    source.setDefaultEncoding(StandardCharsets.UTF_8.name());
    source.setFallbackToSystemLocale(false);
    return source;
  }
}
