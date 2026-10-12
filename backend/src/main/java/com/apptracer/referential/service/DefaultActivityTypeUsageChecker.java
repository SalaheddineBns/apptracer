package com.apptracer.referential.service;

import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;

/**
 * Implémentation par défaut tant que SFD-01 n'existe pas : aucune saisie, donc aucun type utilisé.
 * {@link Fallback} : elle s'efface dès qu'une autre implémentation du port est déclarée.
 */
@Component
@Fallback
class DefaultActivityTypeUsageChecker implements ActivityTypeUsageChecker {

  @Override
  public boolean isUsed(Long activityTypeId) {
    return false;
  }
}
