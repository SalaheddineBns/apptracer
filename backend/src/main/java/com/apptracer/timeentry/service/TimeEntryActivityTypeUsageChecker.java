package com.apptracer.timeentry.service;

import com.apptracer.referential.service.ActivityTypeUsageChecker;
import com.apptracer.timeentry.repository.TimeEntryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RG-02-07 : implémentation du port de {@code referential} sur {@code time_entries}. Remplace
 * l'implémentation par défaut, qui s'efface ({@code @Fallback}).
 */
@Component
public class TimeEntryActivityTypeUsageChecker implements ActivityTypeUsageChecker {

  private final TimeEntryRepository entries;

  public TimeEntryActivityTypeUsageChecker(TimeEntryRepository entries) {
    this.entries = entries;
  }

  /**
   * RG-02-07 : un type est utilisé dès qu'une saisie l'a enregistré, y compris une saisie supprimée
   * logiquement (architecture, H3).
   */
  @Override
  @Transactional(readOnly = true)
  public boolean isUsed(Long activityTypeId) {
    return entries.existsByActivityTypeId(activityTypeId);
  }
}
