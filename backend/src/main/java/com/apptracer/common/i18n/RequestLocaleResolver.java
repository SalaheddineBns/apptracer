package com.apptracer.common.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

/**
 * RG-08-06: language of the messages returned by the server, read from {@code Accept-Language}.
 * French if the preferred language (highest weight) is French ({@code fr}, {@code fr-FR}…), English
 * for any other value or without the header. Also usable outside Spring MVC (security entry
 * points).
 */
@Component("localeResolver")
public class RequestLocaleResolver implements LocaleResolver {

  @Override
  public Locale resolveLocale(HttpServletRequest request) {
    return resolve(request.getHeader("Accept-Language"));
  }

  static Locale resolve(String header) {
    if (header == null || header.isBlank()) {
      return Locale.ENGLISH;
    }
    List<Locale.LanguageRange> ranges;
    try {
      ranges = Locale.LanguageRange.parse(header);
    } catch (IllegalArgumentException e) {
      return Locale.ENGLISH;
    }
    Locale.LanguageRange preferred = null;
    for (Locale.LanguageRange range : ranges) {
      if (range.getWeight() > 0
          && (preferred == null || range.getWeight() > preferred.getWeight())) {
        preferred = range;
      }
    }
    if (preferred == null) {
      return Locale.ENGLISH;
    }
    String tag = preferred.getRange();
    return tag.equals("fr") || tag.startsWith("fr-") ? Locale.FRENCH : Locale.ENGLISH;
  }

  @Override
  public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
    throw new UnsupportedOperationException("La langue vient uniquement de Accept-Language");
  }
}
