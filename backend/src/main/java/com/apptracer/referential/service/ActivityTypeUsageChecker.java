package com.apptracer.referential.service;

/**
 * Port de RG-02-07 : un type est « utilisé » dès qu'une saisie l'a enregistré, y compris une saisie
 * supprimée logiquement (H3). L'implémentation réelle est fournie par SFD-01 ({@code timeentry}).
 */
public interface ActivityTypeUsageChecker {

  boolean isUsed(Long activityTypeId);
}
