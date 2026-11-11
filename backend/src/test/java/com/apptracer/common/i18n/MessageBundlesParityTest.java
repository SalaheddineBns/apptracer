package com.apptracer.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RG-08-06: both message files have exactly the same keys. */
class MessageBundlesParityTest {

  private static final Pattern PARAMETER = Pattern.compile("\\{\\d+}");

  private static Properties load(String name) throws IOException {
    Properties properties = new Properties();
    try (InputStream in = MessageBundlesParityTest.class.getResourceAsStream("/" + name)) {
      assertThat(in).as(name).isNotNull();
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return properties;
  }

  private static Set<String> parameters(String message) {
    Set<String> found = new TreeSet<>();
    Matcher matcher = PARAMETER.matcher(message);
    while (matcher.find()) {
      found.add(matcher.group());
    }
    return found;
  }

  @Test
  @DisplayName("RG-08-06 : messages_fr et messages_en ont exactement les mêmes clés")
  void sameKeysInBothLanguages() throws IOException {
    Properties fr = load("messages_fr.properties");
    Properties en = load("messages_en.properties");

    assertThat(fr.stringPropertyNames()).isNotEmpty();
    assertThat(new TreeSet<>(en.stringPropertyNames()))
        .isEqualTo(new TreeSet<>(fr.stringPropertyNames()));
  }

  @Test
  @DisplayName("RG-08-06 : aucune valeur vide et mêmes paramètres {n} dans les deux langues")
  void sameParametersAndNoEmptyValue() throws IOException {
    Properties fr = load("messages_fr.properties");
    Properties en = load("messages_en.properties");

    for (String key : fr.stringPropertyNames()) {
      assertThat(fr.getProperty(key)).as("fr " + key).isNotBlank();
      assertThat(en.getProperty(key)).as("en " + key).isNotBlank();
      assertThat(parameters(en.getProperty(key)))
          .as("paramètres " + key)
          .isEqualTo(parameters(fr.getProperty(key)));
    }
  }
}
