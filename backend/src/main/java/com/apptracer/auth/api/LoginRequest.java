package com.apptracer.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "jean.dupont@example.com")
        @NotBlank
        @Size(max = 254)
        String email,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 72)
        String password) {}
