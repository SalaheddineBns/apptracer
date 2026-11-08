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

/** Saisies d'activité (SFD-01). Le propriétaire est toujours l'utilisateur du jeton (RG-00-03). */
@Service
public class TimeEntryService {

  /** RG-01-03 : 31 jours calendaires bornes incluses, soit 30 jours d'écart. */
  private static final long MAX_RANGE_GAP_DAYS = 30;

  /** Identifiant qui ne correspond à aucune saisie : rien à ignorer (création). */
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
  private final WeeklyTotalCalculator weeklyCalculator = new WeeklyTotalCalculator();

  public TimeEntryService(
      TimeEntryRepository entries,
      ActivityTypeRepository types,
      ProjectRepository projects,
      AppSettingsService settings,
      Clock clock,
      WeeklyEntriesReader weeklyReader) {
    this.entries = entries;
    this.types = types;
    this.projects = projects;
    this.settings = settings;
    this.durationCalculator = new DurationCalculator();
    this.clock = clock;
    this.weeklyReader = weeklyReader;
  }

  /**
   * RG-01-14 (rôles) est contrôlé par le contrôleur. RG-01-06 : chevauchement contrôlé ici, la
   * contrainte d'exclusion {@code ex_time_entries_no_overlap} couvrant les requêtes simultanées.
   */
  @Transactional
  public TimeEntry create(Long userId, TimeEntryCommand cmd) {
    TimeEntry entry = new TimeEntry(userId);
    applyChecked(entry, cmd, NO_ENTRY);
    // RG-01-11, RG-00-04 : créé par / le alimenté par l'audit JPA
    return entries.saveAndFlush(entry);
  }

  /**
   * RG-01-12 : tous les champs sont modifiables, granularité comprise, avec exactement les mêmes
   * contrôles qu'à la création (RG-02-06 inclus : type et projet doivent être actifs, même
   * inchangés). Le chevauchement ignore la saisie elle-même. RG-01-08, RG-06-04 : durée recalculée
   * et refigée avec la journée standard en vigueur. RG-01-11 : modifié par / le par l'audit JPA.
   *
   * @param version version lue par l'utilisateur ; périmée, la modification est refusée
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
   * Contrôles communs à la création et à la modification, puis application sur la saisie. Tous les
   * contrôles précèdent la première écriture sur l'entité (pas de vidage prématuré). Les champs
   * devenus sans objet après un changement de granularité sont remis à vide (heures, date de fin).
   */
  private void applyChecked(TimeEntry entry, TimeEntryCommand cmd, long excludedId) {
    checkPeriod(cmd);
    checkStartDateNotInFuture(cmd.startDate());
    ActivityType type = resolveType(cmd.activityTypeCode());
    Project project = resolveProject(type, cmd.projectCode());
    checkNoOverlap(entry.getUserId(), cmd, excludedId);

    // endDate renseignée en base pour toutes les granularités (= startDate hors RANGE)
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
    entry.setComment(cmd.comment()); // RG-01-09 : longueur contrôlée par Bean Validation
    // RG-01-08, RG-06-04, RG-00-02 : journée standard lue à l'enregistrement, puis figée avec la
    // durée
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
   * RG-01-11 : suppression logique (qui, quand, horloge applicative), la ligne reste en base.
   * RG-01-10 : seul le propriétaire supprime sa saisie ; absente ou déjà supprimée = introuvable.
   * Aucune version n'est demandée (l'architecture n'en prévoit pas pour DELETE).
   */
  @Transactional
  public void delete(Long userId, Long id) {
    TimeEntry entry = getOwn(userId, id);
    entry.markDeleted(userId, clock.instant());
    // la saisie sort du contrôle de chevauchement (RG-01-06) et de la contrainte partielle
    entries.saveAndFlush(entry);
  }

  /**
   * RG-00-03 : journal de la semaine du compte donné (celui du jeton, passé par le contrôleur).
   * RG-01-11 : saisies supprimées exclues. RG-01-13 : total calculé par {@link
   * WeeklyTotalCalculator}.
   */
  @Transactional(readOnly = true)
  public WeeklyTimeEntries listWeek(Long userId, LocalDate weekStart) {
    // le paramètre doit être un lundi (architecture §4.5) : 400 sur le champ weekStart
    if (weekStart.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
      throw new InvalidRequestException("error.week.not-monday", "weekStart", "field.not-monday");
    }
    List<TimeEntry> week = weeklyReader.read(userId, weekStart);
    var totals = weeklyCalculator.compute(week, weekStart);
    return new WeeklyTimeEntries(
        weekStart, weekStart.plusDays(6), totals.totalMinutes(), totals.minutesPerDay(), week);
  }

  /** RG-01-10, RG-00-03 : seul le propriétaire lit sa saisie ; une saisie supprimée est absente. */
  @Transactional(readOnly = true)
  public TimeEntry getOwn(Long userId, Long id) {
    // RG-01-11 : saisie absente ou supprimée logiquement
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
   * RG-01-06 : intervalle semi-ouvert [début, fin) en heure locale, comme la colonne {@code
   * occupied} : HOUR = [date + début, date + fin), DAY / RANGE = jours entiers, week-ends compris.
   * {@code excludedId} permet à la modification d'ignorer la saisie elle-même (RG-01-12).
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
        // RG-01-02 : la journée entière de startDate, rien à contrôler
      }
    }
  }

  private static void checkHours(LocalTime start, LocalTime end) {
    // RG-01-01 : pas de 15 minutes
    if (!isQuarter(start)) {
      throw BusinessException.onField(
          "RG-01-01", "error.hours.quarter", "startTime", "field.hours.quarter");
    }
    if (!isQuarter(end)) {
      throw BusinessException.onField(
          "RG-01-01", "error.hours.quarter", "endTime", "field.hours.quarter");
    }
    // RG-01-01 : même jour, fin strictement après le début
    if (!end.isAfter(start)) {
      throw BusinessException.onField(
          "RG-01-01", "error.hours.end-after-start", "endTime", "field.hours.end-after-start");
    }
  }

  private static boolean isQuarter(LocalTime t) {
    return t.getNano() == 0 && t.toSecondOfDay() % QUARTER_SECONDS == 0;
  }

  private static void checkRange(LocalDate start, LocalDate end) {
    // RG-01-03 : fin strictement après le début
    if (!end.isAfter(start)) {
      throw BusinessException.onField(
          "RG-01-03", "error.range.end-after-start", "endDate", "field.range.end-after-start");
    }
    // RG-01-03 : 31 jours calendaires au plus, bornes incluses
    if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_GAP_DAYS) {
      throw BusinessException.onField(
          "RG-01-03", "error.range.max-days", "endDate", "field.range.max-days");
    }
  }

  private void checkStartDateNotInFuture(LocalDate startDate) {
    // RG-01-07, RG-00-01 : seule la date de début est contrôlée, à la date du jour Europe/Paris
    if (startDate.isAfter(LocalDate.now(clock))) {
      throw BusinessException.onField(
          "RG-01-07", "error.start-date.future", "startDate", "field.start-date.future");
    }
  }

  private ActivityType resolveType(String code) {
    // RG-01-04 : le type existe et est actif (RG-02-06)
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

  private Project resolveProject(ActivityType type, String projectCode) {
    // RG-01-05 : projet obligatoire si le type l'exige, interdit sinon
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
    // RG-02-06 : le projet existe et est actif
    return projects
        .findByCode(projectCode)
        .filter(Project::isActive)
        .orElseThrow(
            () ->
                BusinessException.onField(
                    "RG-02-06",
                    "error.project.unknown-or-inactive",
                    "projectCode",
                    "field.project.unknown-or-inactive"));
  }
}
