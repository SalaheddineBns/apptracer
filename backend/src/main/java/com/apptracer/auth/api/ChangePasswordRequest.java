package com.apptracer.auth.api;

import com.apptracer.account.api.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 72)
        String currentPassword,
    // RG-03-02 : 12 caractères, une lettre, un chiffre (72 octets au plus)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @ValidPassword
        String newPassword) {

  /** No password in the logs. */
  @Override
  public String toString() {
    return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
  }
}
