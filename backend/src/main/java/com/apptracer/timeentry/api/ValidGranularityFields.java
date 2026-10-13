package com.apptracer.timeentry.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Champs requis ou interdits selon la granularité (RG-01-01 à RG-01-03) : 400 par champ. */
@Documented
@Constraint(validatedBy = ValidGranularityFieldsValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidGranularityFields {

  String message() default "Champs incohérents avec la granularité.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
