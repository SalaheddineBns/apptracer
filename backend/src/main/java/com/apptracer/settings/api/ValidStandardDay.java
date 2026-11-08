package com.apptracer.settings.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RG-06-02 : de 60 à 1 440 minutes, par pas de 15. {@code null} accepté (voir {@code @NotNull}).
 */
@Documented
@Constraint(validatedBy = ValidStandardDayValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidStandardDay {

  String message() default "{validation.standard-day}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
