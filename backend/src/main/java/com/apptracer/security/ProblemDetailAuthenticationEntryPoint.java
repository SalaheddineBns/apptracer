package com.apptracer.security;

import com.apptracer.common.error.ProblemDetails;
import com.apptracer.common.i18n.RequestLocaleResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** RG-03-09 : requête sans access token valide sur une ressource protégée, 401 en ProblemDetail. */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;
  private final MessageSource messages;
  private final RequestLocaleResolver localeResolver;

  public ProblemDetailAuthenticationEntryPoint(
      ObjectMapper objectMapper, MessageSource messages, RequestLocaleResolver localeResolver) {
    this.objectMapper = objectMapper;
    this.messages = messages;
    this.localeResolver = localeResolver;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    // RG-03-09, RG-08-06 : message dans la langue de la requête
    Locale locale = localeResolver.resolveLocale(request);
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(
        response.getWriter(),
        ProblemDetails.of(
            HttpStatus.UNAUTHORIZED,
            "unauthenticated",
            messages.getMessage("error.title.unauthenticated", null, locale),
            messages.getMessage("error.unauthenticated.detail", null, locale),
            "RG-03-09",
            List.of()));
  }
}
