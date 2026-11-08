package com.apptracer.common.i18n;

import java.nio.charset.StandardCharsets;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * RG-08-06 : messages serveur en français et en anglais ({@code messages_fr.properties}, {@code
 * messages_en.properties}, UTF-8). Pas de {@code messages.properties} : Spring Boot ne configure
 * donc pas le {@code MessageSource} lui-même. Jamais de repli sur la langue du système : l'anglais
 * est la langue par défaut, fixée par {@link RequestLocaleResolver}.
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
