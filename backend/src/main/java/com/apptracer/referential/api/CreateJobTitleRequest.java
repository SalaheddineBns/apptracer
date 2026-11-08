package com.apptracer.referential.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateJobTitleRequest(
    // RG-09-05 : lettres, chiffres et _, 2 à 30 caractères, contrôlé tel que saisi. Les minuscules
    // passent ici pour que l'unicité insensible à la casse réponde 409 (CA-09-03) ; le service
    // refuse ensuite un code non écrit en majuscules.
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^[A-Z0-9_]{2,30}$")
        @NotNull
        @Pattern(regexp = "^[A-Za-z0-9_]{2,30}$", message = "{validation.code.pattern}")
        String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String label) {}
