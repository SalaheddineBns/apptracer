package com.apptracer.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthTokensResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String refreshToken,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CurrentUserResponse user) {}
