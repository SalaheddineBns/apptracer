package com.apptracer.timeentry.service;

import com.apptracer.timeentry.domain.TimeEntry;
import com.apptracer.timeentry.repository.TimeEntryRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads one week of time entries for an account passed as a parameter (never read from the security
 * context), reused by the journal (SFD-01) and the team view (SFD-04, RG-04-05).
 */
@Component
public class WeeklyEntriesReader {

  private final TimeEntryRepository entries;

  public WeeklyEntriesReader(TimeEntryRepository entries) {
    this.entries = entries;
  }

  /**
   * RG-01-11: the account's non-deleted time entries that intersect the week {@code [weekStart,
   * weekStart + 6 days]}, RANGE spanning the boundary included (RG-01-13), with type and project
   * embedded even when deactivated (RG-02-05). {@code weekStart} is assumed to be a Monday.
   */
  @Transactional(readOnly = true)
  public List<TimeEntry> read(Long userId, LocalDate weekStart) {
    return entries.findWeek(userId, weekStart, weekStart.plusDays(6));
  }

  /**
   * RG-04-05: same time entries as {@link #read} for several accounts, in one query, grouped by
   * account (an account without entries has no key).
   */
  @Transactional(readOnly = true)
  public java.util.Map<Long, List<TimeEntry>> readAll(
      java.util.Collection<Long> userIds, LocalDate weekStart) {
    if (userIds.isEmpty()) {
      return java.util.Map.of();
    }
    return entries.findWeekForUsers(userIds, weekStart, weekStart.plusDays(6)).stream()
        .collect(java.util.stream.Collectors.groupingBy(TimeEntry::getUserId));
  }
}
