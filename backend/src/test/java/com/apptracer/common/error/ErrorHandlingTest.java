package com.apptracer.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ErrorHandlingTest.ProbeConfig.class})
class ErrorHandlingTest {

  record ProbeRequest(@NotBlank @Size(max = 5) String name) {}

  @RestController
  static class ProbeController {
    @PostMapping("/api/v1/probe/validation")
    void validation(@Valid @RequestBody ProbeRequest request) {}

    @GetMapping("/api/v1/probe/business")
    void business() {
      throw BusinessException.onField(
          "RG-99-01", "Règle de test violée.", "endTime", "doit être après l'heure de début");
    }

    @GetMapping("/api/v1/probe/business-no-field")
    void businessNoField() {
      throw new BusinessException("RG-99-02", "Autre règle violée.");
    }

    @GetMapping("/api/v1/probe/version")
    void version() {
      throw new ObjectOptimisticLockingFailureException(Object.class, 1L);
    }

    @GetMapping("/api/v1/probe/admin")
    @PreAuthorize("hasRole('ADMIN')")
    void admin() {}
  }

  @TestConfiguration
  static class ProbeConfig {
    @Bean
    ProbeController probeController() {
      return new ProbeController();
    }
  }

  @Autowired private MockMvc mockMvc;

  @Test
  @WithMockUser
  @DisplayName("RG-03-09 : violation de validation, 400 avec errors[] par champ en français")
  void validationErrorReturns400WithFieldErrors() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/probe/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Requête invalide"))
        .andExpect(jsonPath("$.code").doesNotExist())
        .andExpect(jsonPath("$.errors[0].field").value("name"))
        .andExpect(jsonPath("$.errors[0].message").value("ne doit pas être vide"));
  }

  @Test
  @WithMockUser
  @DisplayName("Corps JSON illisible : 400 ProblemDetail")
  void unreadableBodyReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/probe/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  @Test
  @WithMockUser
  @DisplayName("BusinessException : 409 avec le code de la RG et errors[]")
  void businessExceptionReturns409WithCode() throws Exception {
    mockMvc
        .perform(get("/api/v1/probe/business"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("RG-99-01"))
        .andExpect(jsonPath("$.detail").value("Règle de test violée."))
        .andExpect(jsonPath("$.errors[0].field").value("endTime"))
        .andExpect(jsonPath("$.errors[0].message").value("doit être après l'heure de début"));
  }

  @Test
  @WithMockUser
  @DisplayName("BusinessException sans champ : pas de errors[]")
  void businessExceptionWithoutFieldHasNoErrors() throws Exception {
    mockMvc
        .perform(get("/api/v1/probe/business-no-field"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-99-02"))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  @WithMockUser
  @DisplayName("Conflit de version : 409 concurrent-modification sans code")
  void optimisticLockReturns409WithoutCode() throws Exception {
    mockMvc
        .perform(get("/api/v1/probe/version"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("https://apptracer/errors/concurrent-modification"))
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  @Test
  @DisplayName("RG-03-09 : sans access token, 401 ProblemDetail")
  void unauthenticatedReturns401ProblemDetail() throws Exception {
    mockMvc
        .perform(get("/api/v1/probe/admin"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("RG-03-09"))
        .andExpect(jsonPath("$.detail").isNotEmpty());
  }

  @Test
  @WithMockUser(roles = "USER")
  @DisplayName("RG-03-09 : rôle insuffisant, 403 ProblemDetail")
  void insufficientRoleReturns403ProblemDetail() throws Exception {
    mockMvc
        .perform(get("/api/v1/probe/admin"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("RG-03-09"));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("RG-03-09 : rôle suffisant, accès autorisé")
  void sufficientRoleIsAllowed() throws Exception {
    mockMvc.perform(get("/api/v1/probe/admin")).andExpect(status().isOk());
  }
}
