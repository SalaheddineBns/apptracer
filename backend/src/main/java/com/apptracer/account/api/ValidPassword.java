package com.apptracer.account.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Mot de passe conforme à RG-03-02 (et à la limite BCrypt de 72 octets). {@code null} accepté. */
@Documented
@Constraint(validatedBy = ValidPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

  String message() default "Mot de passe invalide";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
