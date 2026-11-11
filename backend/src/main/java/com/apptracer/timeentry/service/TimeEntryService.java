package com.apptracer.timeentry.service;

import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.ForbiddenOperationException;
import com.apptracer.common.error.InvalidRequestException;
import com.apptracer.common.error.ResourceNotFoundException;
import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.domain.Project;
import com.apptracer.referential.repository.ActivityTypeRepository;
import com.apptracer.referential.repository.ProjectRepository;
import com.apptracer.settings.service.AppSettingsService;
import com.apptracer.team.service.TeamProjectScope;
import com.apptracer.timeentry.domain.Granularity;
import com.apptracer.timeentry.domain.TimeEntry;
import com.apptracer.timeentry.repository.TimeEntryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Activity time entries (SFD-01). The owner is always the user of the token (RG-00-03). */
@Service
public class TimeEntryService {

  /** RG-01-03: 31 calendar days, bounds included, that is a 30-day gap. */
  private static final long MAX_RANGE_GAP_DAYS = 30;

  /** Identifier that matches no time entry: nothing to ignore (creation). */
  private static final long NO_ENTRY = -1L;

  private static final DateTimeFormatter FRENCH_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private static final int QUARTER_SECONDS = 15 * 60;

  private final TimeEntryRepository entries;
  private final ActivityTypeRepository types;
  private final ProjectRepository projects;
  private final AppSettingsService settings;
  private final DurationCalculator durationCalculator;
  private final Clock clock;
  private final WeeklyEntriesReader weeklyReader;
  private final TeamProjectScope teamScope;
  private final WeeklyTotalCalculator weeklyCalculator = new WeeklyTotalCalculator();

  public TimeEntryService(
      TimeEntryRepository entries,
      ActivityTypeRepository types,
      ProjectRepository projects,
      AppSettingsService settings,
      Clock clock,
      WeeklyEntriesReader weeklyReader,
      TeamProjectScope teamScope) {
    this.entries = entries;
    this.types = types;
    this.projects = projects;
    this.settings = settings;
    this.durationCalculator = new DurationCalculator();
    this.clock = clock;
    this.weeklyReader = weeklyReader;
    this.teamScope = teamScope;
  }

  /**
   * RG-01-14 (roles) is checked by the controller. RG-01-06: overlap is checked here, with the
   * exclusion constraint {@code ex_time_entries_no_overlap} covering concurrent requests.
   */
  @Transactional
  public TimeEntry create(Long userId, TimeEntryCommand cmd) {
    TimeEntry entry = new TimeEntry(userId);
    applyChecked(entry, cmd, NO_ENTRY);
    // RG-01-11, RG-00-04: created by / at filled in by JPA auditing
    return entries.saveAndFlush(entry);
  }

  /**
   * RG-01-12: every field can be changed, granularity included, with exactly the same checks as at
   * creation (RG-02-06 included: type and project must be active, even unchanged). The overlap
   * check ignores the entry itself. RG-01-08, RG-06-04: the duration is recalculated and frozen
   * with the standard day in force. RG-01-11: modified by / at through JPA auditing.
   *
   * @param version version read by the user; if outdated, the change is refused
   */
  @Transactional
  public TimeEntry update(Long userId, Long id, long version, TimeEntryCommand cmd) {
    TimeEntry entry = getOwn(userId, id); // RG-01-10 (403), RG-01-11 (404)
    if (entry.getVersion() != version) {
      throw new ObjectOptimisticLockingFailureException(TimeEntry.class, id);
    }
    applyChecked(entry, cmd, id);
    return entries.saveAndFlush(entry);
  }

  /**
   * Checks shared by creation and update, then applied to the time entry. All checks run before the
   * first write to the entity (no premature flush). Fields that no longer apply after a granularity
   * change are cleared (times, end date).
   */
  private void applyChecked(TimeEntry entry, TimeEntryCommand cmd, long excludedId) {
    checkPeriod(cmd);
    checkStartDateNotInFuture(cmd.startDate());
    ActivityType type = resolveType(cmd.activityTypeCode());
    Project project = resolveProject(type, cmd.projectCode(), entry);
    checkNoOverlap(entry.getUserId(), cmd, excludedId);

    // endDate stored in the database for every granularity (= startDate except for RANGE)
    boolean range = cmd.granularity() == Granularity.RANGE;
    boolean hour = cmd.granularity() == Granularity.HOUR;
    LocalDate endDate = range ? cmd.endDate() : cmd.startDate();
    entry.setPeriod(
        cmd.granularity(),
        cmd.startDate(),
        endDate,
        hour ? cmd.startTime() : null,
        hour ? cmd.endTime() : null);
    entry.setActivityType(type);
    entry.setProject(project);
    entry.setComment(cmd.comment()); // RG-01-09: length checked by Bean Validation
    // RG-01-08, RG-06-04, RG-00-02: standard day read on save, then frozen with the duration
    int standardDay = settings.getStandardDayMinutes();
    entry.setDuration(
        durationCalculator.compute(
            cmd.granularity(),
            cmd.startDate(),
            endDate,
            entry.getStartTime(),
            entry.getEndTime(),
            standardDay),
        standardDay);
  }

  /**
   * RG-01-11: logical deletion (who, when, application clock); the row stays in the database.
   * RG-01-10: only the owner deletes their time entry; missing or already deleted means not found.
   * No version is required (the architecture does not provide one for DELETE).
   */
  @Transactional
  public void delete(Long userId, Long id) {
    TimeEntry entry = getOwn(userId, id);
    entry.markDeleted(userId, clock.instant());
    // the entry leaves the overlap check (RG-01-06) and the partial constraint
    entries.saveAndFlush(entry);
  }

  /**
   * RG-00-03: journal of the week of the given account (the token's, passed by the controller).
   * RG-01-11: deleted time entries excluded. RG-01-13: total computed by {@link
   * WeeklyTotalCalculator}.
   */
  @Transactional(readOnly = true)
  public WeeklyTimeEntries listWeek(Long userId, LocalDate weekStart) {
    // the parameter must be a Monday (architecture §4.5): 400 on the weekStart field
    if (weekStart.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
      throw new InvalidRequestException("error.week.not-monday", "weekStart", "field.not-monday");
    }
    List<TimeEntry> week = weeklyReader.read(userId, weekStart);
    var totals = weeklyCalculator.compute(week, weekStart);
    return new WeeklyTimeEntries(
        weekStart, weekStart.plusDays(6), totals.totalMinutes(), totals.minutesPerDay(), week);
  }

  /** RG-01-10, RG-00-03: only the owner reads their time entry; a deleted entry is missing. */
  @Transactional(readOnly = true)
  public TimeEntry getOwn(Long userId, Long id) {
    // RG-01-11: missing or logically deleted time entry
    TimeEntry entry =
        entries
            .findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.time-entry.not-found"));
    // RG-01-10
    if (!entry.getUserId().equals(userId)) {
      throw new ForbiddenOperationException("RG-01-10", "error.time-entry.not-owner");
    }
    return entry;
  }

  /**
   * RG-01-06: half-open interval [start, end) in local time, like the {@code occupied} column: HOUR
   * = [date + start, date + end), DAY / RANGE = whole days, weekends included. {@code excludedId}
   * lets an update ignore the entry itself (RG-01-12).
   */
  private void checkNoOverlap(Long userId, TimeEntryCommand cmd, long excludedId) {
    LocalDateTime from;
    LocalDateTime to;
    if (cmd.granularity() == Granularity.HOUR) {
      from = cmd.startDate().atTime(cmd.startTime());
      to = cmd.startDate().atTime(cmd.endTime());
    } else {
      LocalDate last = cmd.granularity() == Granularity.RANGE ? cmd.endDate() : cmd.startDate();
      from = cmd.startDate().atStartOfDay();
      to = last.plusDays(1).atStartOfDay();
    }
    entries.findFirstOverlapDay(userId, from, to, excludedId).stream()
        .findFirst()
        .ifPresent(
            day -> {
              throw new BusinessException(
                  "RG-01-06", "error.time-entry.overlap", FRENCH_DATE.format(LocalDate.parse(day)));
            });
  }

  private void checkPeriod(TimeEntryCommand cmd) {
    switch (cmd.granularity()) {
      case HOUR -> checkHours(cmd.startTime(), cmd.endTime());
      case RANGE -> checkRange(cmd.startDate(), cmd.endDate());
      case DAY -> {
        // RG-01-02: the whole day of startDate, nothing to check
      }
    }
  }

  private static void checkHours(LocalTime start, LocalTime end) {
    // RG-01-01: 15-minute steps
    if (!isQuarter(start)) {
      throw BusinessException.onField(
          "RG-01-01", "error.hours.quarter", "startTime", "field.hours.quarter");
    }
    if (!isQuarter(end)) {
      throw BusinessException.onField(
          "RG-01-01", "error.hours.quarter", "endTime", "field.hours.quarter");
    }
    // RG-01-01: same day, end strictly after start
    if (!end.isAfter(start)) {
      throw BusinessException.onField(
          "RG-01-01", "error.hours.end-after-start", "endTime", "field.hours.end-after-start");
    }
  }

  private static boolean isQuarter(LocalTime t) {
    return t.getNano() == 0 && t.toSecondOfDay() % QUARTER_SECONDS == 0;
  }

  private static void checkRange(LocalDate start, LocalDate end) {
    // RG-01-03: end strictly after start
    if (!end.isAfter(start)) {
      throw BusinessException.onField(
          "RG-01-03", "error.range.end-after-start", "endDate", "field.range.end-after-start");
    }
    // RG-01-03: 31 calendar days at most, bounds included
    if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_GAP_DAYS) {
      throw BusinessException.onField(
          "RG-01-03", "error.range.max-days", "endDate", "field.range.max-days");
    }
  }

  private void checkStartDateNotInFuture(LocalDate startDate) {
    // RG-01-07, RG-00-01: only the start date is checked, against today's date in Europe/Paris
    if (startDate.isAfter(LocalDate.now(clock))) {
      throw BusinessException.onField(
          "RG-01-07", "error.start-date.future", "startDate", "field.start-date.future");
    }
  }

  private ActivityType resolveType(String code) {
    // RG-01-04: the type exists and is active (RG-02-06)
    return types
        .findByCode(code)
        .filter(ActivityType::isActive)
        .orElseThrow(
            () ->
                BusinessException.onField(
                    "RG-01-04",
                    "error.activity-type.unknown-or-inactive",
                    "activityTypeCode",
                    "field.activity-type.unknown-or-inactive"));
  }

  /**
   * RG-01-05, RG-02-06: project required or forbidden depending on the type, existing and active.
   * RG-10-03, RG-10-04: a new project must have no team or belong to one of the author's teams;
   * RG-10-05: on update, the project already on the entry is still accepted.
   */
  private Project resolveProject(ActivityType type, String projectCode, TimeEntry entry) {
    // RG-01-05: project required if the type requires it, forbidden otherwise
    if (type.isProjectRequired() && projectCode == null) {
      throw BusinessException.onField(
          "RG-01-05", "error.project.required", "projectCode", "field.project.required");
    }
    if (!type.isProjectRequired()) {
      if (projectCode != null) {
        throw BusinessException.onField(
            "RG-01-05", "error.project.not-allowed", "projectCode", "field.project.not-allowed");
      }
      return null;
    }
    // RG-02-06: the project exists and is active
    Project project =
        projects
            .findByCode(projectCode)
            .filter(Project::isActive)
            .orElseThrow(
                () ->
                    BusinessException.onField(
                        "RG-02-06",
                        "error.project.unknown-or-inactive",
                        "projectCode",
                        "field.project.unknown-or-inactive"));
    Project current = entry.getProject();
    boolean unchanged = current != null && current.getId().equals(project.getId());
    if (!unchanged
        && project.getTeam() != null
        && !teamScope.teamIdsOf(entry.getUserId()).contains(project.getTeam().getId())) {
      throw BusinessException.onField(
          "RG-10-04", "error.project.not-in-team", "projectCode", "field.project.not-in-team");
    }
    return project;
  }
}
