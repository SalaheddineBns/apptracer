package com.apptracer.account.api;

import com.apptracer.account.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    // RG-03-01 : format e-mail valide
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email @Size(max = 254)
        String email,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100)
        String firstName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String lastName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) @NotNull Role role,
    // RG-03-02 : 12 caractères, une lettre, un chiffre ; RG-03-12 : défini par l'ADMIN
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @ValidPassword
        String initialPassword,
    // RG-09-02, RG-09-04 : fonction facultative (code), pour un USER ou un MANAGER
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String jobTitleCode) {

  /** Le mot de passe ne doit jamais apparaître dans un log. */
  @Override
  public String toString() {
    return "CreateUserRequest[email=" + email + ", role=" + role + ", initialPassword=***]";
  }
}
