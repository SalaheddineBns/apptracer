package com.apptracer.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String refreshToken) {}
