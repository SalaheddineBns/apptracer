package com.apptracer.timeentry.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class ValidGranularityFieldsValidator
    implements ConstraintValidator<ValidGranularityFields, CreateTimeEntryRequest> {

  @Override
  public boolean isValid(CreateTimeEntryRequest r, ConstraintValidatorContext ctx) {
    if (r.granularity() == null) {
      return true; // déjà signalé par @NotNull
    }
    ctx.disableDefaultConstraintViolation();
    boolean valid = true;
    switch (r.granularity()) {
      case HOUR -> {
        // RG-01-01 : heures requises, pas de date de fin
        valid &= required(ctx, r.startTime() != null, "startTime");
        valid &= required(ctx, r.endTime() != null, "endTime");
        valid &= forbidden(ctx, r.endDate() == null, "endDate");
      }
      case DAY -> {
        // RG-01-02 : journée entière de startDate
        valid &= forbidden(ctx, r.endDate() == null, "endDate");
        valid &= forbidden(ctx, r.startTime() == null, "startTime");
        valid &= forbidden(ctx, r.endTime() == null, "endTime");
      }
      case RANGE -> {
        // RG-01-03
        valid &= required(ctx, r.endDate() != null, "endDate");
        valid &= forbidden(ctx, r.startTime() == null, "startTime");
        valid &= forbidden(ctx, r.endTime() == null, "endTime");
      }
    }
    return valid;
  }

  private static boolean required(ConstraintValidatorContext ctx, boolean ok, String field) {
    return check(ctx, ok, field, "Ce champ est obligatoire pour cette granularité.");
  }

  private static boolean forbidden(ConstraintValidatorContext ctx, boolean ok, String field) {
    return check(ctx, ok, field, "Ce champ n'est pas autorisé pour cette granularité.");
  }

  private static boolean check(
      ConstraintValidatorContext ctx, boolean ok, String field, String message) {
    if (!ok) {
      ctx.buildConstraintViolationWithTemplate(message)
          .addPropertyNode(field)
          .addConstraintViolation();
    }
    return ok;
  }
}
