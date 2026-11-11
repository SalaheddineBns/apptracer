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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** RG-03-09: insufficient role, 403 as a ProblemDetail. */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;
  private final MessageSource messages;
  private final RequestLocaleResolver localeResolver;

  public ProblemDetailAccessDeniedHandler(
      ObjectMapper objectMapper, MessageSource messages, RequestLocaleResolver localeResolver) {
    this.objectMapper = objectMapper;
    this.messages = messages;
    this.localeResolver = localeResolver;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    // RG-03-09, RG-08-06 : message dans la langue de la requête
    Locale locale = localeResolver.resolveLocale(request);
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(
        response.getWriter(),
        ProblemDetails.of(
            HttpStatus.FORBIDDEN,
            "forbidden",
            messages.getMessage("error.title.forbidden", null, locale),
            messages.getMessage("error.access-denied.detail", null, locale),
            "RG-03-09",
            List.of()));
  }
}
