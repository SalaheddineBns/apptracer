package com.apptracer.common.i18n;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.security.AccessTokenService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** RG-08-06 : messages du serveur dans la langue de {@code Accept-Language}. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LocalizedMessagesTest {

  private static final String FRENCH_OVERLAP =
      "Cette période chevauche une activité déjà déclarée le 05/10/2026.";
  private static final String ENGLISH_OVERLAP =
      "This time entry overlaps one already declared on 05/10/2026.";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User user;

  @BeforeEach
  void setUp() {
    user = users.save(new User("i18n-user@example.com", "I", "User", "hash", Role.USER));
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM time_entries");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  private String bearer() {
    return "Bearer " + tokens.issue(user.getId(), user.getRole(), UUID.randomUUID());
  }

  private ResultActions postEntry(String language, String body) throws Exception {
    MockHttpServletRequestBuilder request =
        post("/api/v1/time-entries")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    if (language != null) {
      request.header(HttpHeaders.ACCEPT_LANGUAGE, language);
    }
    return mockMvc.perform(request);
  }

  private ResultActions createHour(String language, String from, String to) throws Exception {
    return postEntry(
        language,
        ("{\"granularity\":\"HOUR\",\"startDate\":\"2026-10-05\",\"startTime\":\"%s\","
                + "\"endTime\":\"%s\",\"activityTypeCode\":\"REUNION\"}")
            .formatted(from, to));
  }

  private void overlapMessageIs(String language, String expected) throws Exception {
    createHour(language, "09:30", "10:30")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-01-06"))
        .andExpect(jsonPath("$.detail").value(expected));
  }

  @Test
  @DisplayName("CA-08-05 : chevauchement RG-01-06 avec Accept-Language en, message en anglais")
  void overlapInEnglish() throws Exception {
    createHour("en", "09:00", "10:00").andExpect(status().isCreated());
    overlapMessageIs("en", ENGLISH_OVERLAP);
  }

  @Test
  @DisplayName("CA-08-05 : chevauchement RG-01-06 avec Accept-Language fr, message en français")
  void overlapInFrench() throws Exception {
    createHour("fr", "09:00", "10:00").andExpect(status().isCreated());
    overlapMessageIs("fr", FRENCH_OVERLAP);
    overlapMessageIs("fr-FR,fr;q=0.9,en;q=0.8", FRENCH_OVERLAP);
  }

  @Test
  @DisplayName("CA-08-05 : sans en-tête Accept-Language, le message est en anglais")
  void overlapWithoutHeaderIsEnglish() throws Exception {
    createHour(null, "09:00", "10:00").andExpect(status().isCreated());
    overlapMessageIs(null, ENGLISH_OVERLAP);
  }

  @Test
  @DisplayName("CA-08-05 : une langue préférée autre que le français donne l'anglais")
  void otherPreferredLanguageIsEnglish() throws Exception {
    createHour("en", "09:00", "10:00").andExpect(status().isCreated());
    overlapMessageIs("de", ENGLISH_OVERLAP);
    overlapMessageIs("de,fr;q=0.5", ENGLISH_OVERLAP);
  }

  @Test
  @DisplayName("RG-08-06 : le code de RG et le champ ne changent pas avec la langue")
  void codesAndFieldsAreNotTranslated() throws Exception {
    for (String language : new String[] {"fr", "en"}) {
      createHour(language, "09:10", "10:00")
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("RG-01-01"))
          .andExpect(jsonPath("$.errors[0].field").value("startTime"));
    }
    createHour("fr", "09:10", "10:00")
        .andExpect(jsonPath("$.errors[0].message").value("Doit être un multiple de 15 minutes."));
    createHour("en", "09:10", "10:00")
        .andExpect(jsonPath("$.errors[0].message").value("Must be a multiple of 15 minutes."));
  }

  @Test
  @DisplayName("RG-08-06 : erreur de validation Bean Validation localisée")
  void validationIsLocalized() throws Exception {
    String body =
        "{\"granularity\":\"DAY\",\"startDate\":\"2026-10-05\",\"activityTypeCode\":\"REUNION\","
            + "\"comment\":\""
            + "x".repeat(501)
            + "\"}";
    postEntry("en", body)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value("The request is invalid. Correct the highlighted fields."))
        .andExpect(jsonPath("$.errors[0].field").value("comment"))
        .andExpect(jsonPath("$.errors[0].message").value("500 characters maximum."));
    postEntry("fr", body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].message").value("500 caractères au plus."));
  }

  @Test
  @DisplayName("RG-08-06 : 401, 403 et 404 localisés")
  void securityAndNotFoundAreLocalized() throws Exception {
    mockMvc
        .perform(get("/api/v1/users").header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("RG-03-09"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Authentification requise : connectez-vous pour accéder à cette ressource."));
    mockMvc
        .perform(get("/api/v1/users"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            jsonPath("$.detail")
                .value("Authentication required: sign in to access this resource."));
    mockMvc
        .perform(
            get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-03-09"))
        .andExpect(
            jsonPath("$.detail")
                .value("Access denied: you do not have sufficient rights for this action."));
    mockMvc
        .perform(
            get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.detail")
                .value("Accès refusé : vos droits sont insuffisants pour cette action."));
    mockMvc
        .perform(
            get("/api/v1/time-entries/999999")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Entry not found."));
  }
}
