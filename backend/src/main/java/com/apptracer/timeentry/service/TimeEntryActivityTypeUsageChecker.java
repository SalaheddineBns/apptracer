package com.apptracer.timeentry.service;

import com.apptracer.referential.service.ActivityTypeUsageChecker;
import com.apptracer.timeentry.repository.TimeEntryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RG-02-07: implementation of the {@code referential} port on {@code time_entries}. Replaces the
 * default implementation, which steps aside ({@code @Fallback}).
 */
@Component
public class TimeEntryActivityTypeUsageChecker implements ActivityTypeUsageChecker {

  private final TimeEntryRepository entries;

  public TimeEntryActivityTypeUsageChecker(TimeEntryRepository entries) {
    this.entries = entries;
  }

  /**
   * RG-02-07: a type is in use as soon as a time entry has recorded it, including a logically
   * deleted entry (architecture, H3).
   */
  @Override
  @Transactional(readOnly = true)
  public boolean isUsed(Long activityTypeId) {
    return entries.existsByActivityTypeId(activityTypeId);
  }
}
