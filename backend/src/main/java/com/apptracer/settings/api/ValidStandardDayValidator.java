package com.apptracer.settings.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** RG-06-02. */
public class ValidStandardDayValidator implements ConstraintValidator<ValidStandardDay, Integer> {

  @Override
  public boolean isValid(Integer minutes, ConstraintValidatorContext context) {
    return minutes == null || (minutes >= 60 && minutes <= 1440 && minutes % 15 == 0);
  }
}
