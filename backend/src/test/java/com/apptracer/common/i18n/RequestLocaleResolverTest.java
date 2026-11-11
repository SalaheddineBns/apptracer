package com.apptracer.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RG-08-06: French if the preferred language is French, English otherwise. */
class RequestLocaleResolverTest {

  @Test
  @DisplayName("RG-08-06 : fr, fr-FR et fr en tête donnent le français")
  void frenchWhenPreferred() {
    assertThat(RequestLocaleResolver.resolve("fr")).isEqualTo(Locale.FRENCH);
    assertThat(RequestLocaleResolver.resolve("fr-FR")).isEqualTo(Locale.FRENCH);
    assertThat(RequestLocaleResolver.resolve("fr-CH, fr;q=0.9, en;q=0.8")).isEqualTo(Locale.FRENCH);
    assertThat(RequestLocaleResolver.resolve("en;q=0.5, fr;q=0.9")).isEqualTo(Locale.FRENCH);
  }

  @Test
  @DisplayName("RG-08-06 : absence d'en-tête, valeur invalide ou autre langue donnent l'anglais")
  void englishOtherwise() {
    assertThat(RequestLocaleResolver.resolve(null)).isEqualTo(Locale.ENGLISH);
    assertThat(RequestLocaleResolver.resolve("")).isEqualTo(Locale.ENGLISH);
    assertThat(RequestLocaleResolver.resolve("en-GB")).isEqualTo(Locale.ENGLISH);
    assertThat(RequestLocaleResolver.resolve("de")).isEqualTo(Locale.ENGLISH);
    assertThat(RequestLocaleResolver.resolve("de, fr;q=0.5")).isEqualTo(Locale.ENGLISH);
    assertThat(RequestLocaleResolver.resolve("*")).isEqualTo(Locale.ENGLISH);
    assertThat(RequestLocaleResolver.resolve("fr;q=0, en")).isEqualTo(Locale.ENGLISH);
    assertThat(RequestLocaleResolver.resolve("pas@une@langue")).isEqualTo(Locale.ENGLISH);
  }
}
