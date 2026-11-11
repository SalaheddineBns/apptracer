package com.apptracer.timeentry.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Fields required or forbidden depending on the granularity (RG-01-01 to RG-01-03): 400 per field.
 */
@Documented
@Constraint(validatedBy = ValidGranularityFieldsValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidGranularityFields {

  String message() default "{validation.granularity-fields}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
