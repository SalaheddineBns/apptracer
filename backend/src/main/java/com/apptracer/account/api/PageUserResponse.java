package com.apptracer.account.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PageUserResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UserResponse> content,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Numéro de page, base 0")
        int page,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements) {}
