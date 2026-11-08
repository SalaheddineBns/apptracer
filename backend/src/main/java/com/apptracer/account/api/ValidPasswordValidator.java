package com.apptracer.account.api;

import com.apptracer.account.service.PasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    List<String> violations = PasswordPolicy.violations(value);
    if (violations.isEmpty()) {
      return true;
    }
    context.disableDefaultConstraintViolation();
    context
        .buildConstraintViolationWithTemplate("{" + violations.get(0) + "}")
        .addConstraintViolation();
    return false;
  }
}
