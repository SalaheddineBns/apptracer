package com.apptracer.timeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.ForbiddenOperationException;
import com.apptracer.common.error.ResourceNotFoundException;
import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.domain.Project;
import com.apptracer.referential.domain.TagColor;
import com.apptracer.referential.repository.ActivityTypeRepository;
import com.apptracer.referential.repository.ProjectRepository;
import com.apptracer.settings.service.AppSettingsService;
import com.apptracer.timeentry.domain.Granularity;
import com.apptracer.timeentry.domain.TimeEntry;
import com.apptracer.timeentry.repository.TimeEntryRepository;
import com.apptracer.timeentry.service.TimeEntryCommand;
import com.apptracer.timeentry.service.TimeEntryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeEntryServiceTest {

  // 14/10/2026 23:30 UTC = 15/10/2026 01:30 à Paris : le jour métier est le 15
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-14T23:30:00Z"), ZoneId.of("Europe/Paris"));
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
  private static final LocalDate D = LocalDate.of(2026, 10, 5);
  private static final Long USER = 7L;

  private final TimeEntryRepository entries = mock(TimeEntryRepository.class);
  private final ActivityTypeRepository types = mock(ActivityTypeRepository.class);
  private final ProjectRepository projects = mock(ProjectRepository.class);
  private final AppSettingsService settings = mock(AppSettingsService.class);
  private final com.apptracer.timeentry.service.WeeklyEntriesReader reader =
      mock(com.apptracer.timeentry.service.WeeklyEntriesReader.class);
  private final TimeEntryService service =
      new TimeEntryService(entries, types, projects, settings, CLOCK, reader);

  private final ActivityType meeting = new ActivityType("REUNION", "Réunion", false, TagColor.WARN);
  private final ActivityType work =
      new ActivityType("TRAVAIL_PROJET", "Travail projet", true, TagColor.INFO);
  private final Project project = new Project("P1", "Projet un");

  @BeforeEach
  void setUp() {
    when(types.findByCode("REUNION")).thenReturn(Optional.of(meeting));
    when(types.findByCode("TRAVAIL_PROJET")).thenReturn(Optional.of(work));
    when(projects.findByCode("P1")).thenReturn(Optional.of(project));
    when(settings.getStandardDayMinutes()).thenReturn(420);
    when(entries.saveAndFlush(any(TimeEntry.class))).thenAnswer(i -> i.getArgument(0));
  }

  private static TimeEntryCommand hour(LocalTime from, LocalTime to) {
    return new TimeEntryCommand(Granularity.HOUR, D, null, from, to, "REUNION", null, null);
  }

  private static TimeEntryCommand day(LocalDate date) {
    return new TimeEntryCommand(Granularity.DAY, date, null, null, null, "REUNION", null, null);
  }

  private static TimeEntryCommand range(LocalDate from, LocalDate to) {
    return new TimeEntryCommand(Granularity.RANGE, from, to, null, null, "REUNION", null, null);
  }

  private void assertRejected(TimeEntryCommand cmd, String rg, String field) {
    clearInvocations(entries);
    assertThatThrownBy(() -> service.create(USER, cmd))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getRgCode()).isEqualTo(rg);
              assertThat(e.getErrors()).extracting("field").containsExactly(field);
            });
    verify(entries, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("CA-01-01 : 09:00-10:30 en REUNION crée une saisie de 90 min pour l'utilisateur")
  void createsHourEntry() {
    TimeEntry e = service.create(USER, hour(LocalTime.of(9, 0), LocalTime.of(10, 30)));
    assertThat(e.getUserId()).isEqualTo(USER);
    assertThat(e.getDurationMinutes()).isEqualTo(90); // RG-01-08
    assertThat(e.getStandardDayMinutes()).isEqualTo(420);
    assertThat(e.getEndDate()).isEqualTo(D);
    assertThat(e.getProject()).isNull();
  }

  @Test
  @DisplayName("CA-01-02 : RG-01-01, fin égale ou antérieure au début refusée sur endTime")
  void endNotAfterStart() {
    assertRejected(hour(LocalTime.of(10, 0), LocalTime.of(9, 0)), "RG-01-01", "endTime");
    assertRejected(hour(LocalTime.of(10, 0), LocalTime.of(10, 0)), "RG-01-01", "endTime");
  }

  @Test
  @DisplayName("RG-01-01 : heures hors pas de 15 minutes refusées, 09:15 et 23:45 acceptées")
  void quarterStep() {
    assertRejected(hour(LocalTime.of(9, 10), LocalTime.of(10, 0)), "RG-01-01", "startTime");
    assertRejected(hour(LocalTime.of(9, 0), LocalTime.of(10, 14)), "RG-01-01", "endTime");
    assertRejected(hour(LocalTime.of(9, 0), LocalTime.of(10, 0, 30)), "RG-01-01", "endTime");
    service.create(USER, hour(LocalTime.of(9, 15), LocalTime.of(9, 30)));
    service.create(USER, hour(LocalTime.of(23, 30), LocalTime.of(23, 45)));
  }

  @Test
  @DisplayName("RG-01-02 : JOURNEE enregistrée sans heures, endDate égale à startDate")
  void dayEntry() {
    TimeEntry e = service.create(USER, day(LocalDate.of(2026, 10, 10))); // samedi
    assertThat(e.getStartTime()).isNull();
    assertThat(e.getEndTime()).isNull();
    assertThat(e.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 10));
    assertThat(e.getDurationMinutes()).isEqualTo(420);
  }

  @Test
  @DisplayName("CA-01-03 : RG-01-03, PLAGE du 05/10 au 16/10/2026 = 4200 min")
  void rangeDuration() {
    TimeEntry e = service.create(USER, range(D, LocalDate.of(2026, 10, 16)));
    assertThat(e.getDurationMinutes()).isEqualTo(4200);
  }

  @Test
  @DisplayName("RG-01-03 : PLAGE de 2 et de 31 jours acceptées, d'un jour et de 32 jours refusées")
  void rangeBounds() {
    service.create(USER, range(D, D.plusDays(1)));
    service.create(USER, range(D, D.plusDays(30)));
    assertRejected(range(D, D), "RG-01-03", "endDate");
    assertRejected(range(D, D.minusDays(1)), "RG-01-03", "endDate");
    assertRejected(range(D, D.plusDays(31)), "RG-01-03", "endDate");
  }

  @Test
  @DisplayName("CA-01-07 : RG-01-07, date de début future refusée, aujourd'hui (Paris) acceptée")
  void startDateNotInFuture() {
    assertRejected(day(TODAY.plusDays(1)), "RG-01-07", "startDate");
    service.create(USER, day(TODAY)); // 01:30 à Paris, encore le 14 en UTC
  }

  @Test
  @DisplayName("RG-01-07 : une PLAGE peut se terminer dans le futur, un créneau HOUR plus tard")
  void onlyStartDateChecked() {
    service.create(USER, range(TODAY, TODAY.plusDays(5)));
    service.create(
        USER,
        new TimeEntryCommand(
            Granularity.HOUR,
            TODAY,
            null,
            LocalTime.of(22, 0),
            LocalTime.of(23, 0),
            "REUNION",
            null,
            null));
  }

  @Test
  @DisplayName("RG-01-04 : type inconnu ou inactif refusé sur activityTypeCode")
  void typeMustExistAndBeActive() {
    ActivityType off = new ActivityType("OFF", "Off", false, TagColor.INFO);
    off.setActive(false);
    when(types.findByCode("OFF")).thenReturn(Optional.of(off));
    when(types.findByCode("NOPE")).thenReturn(Optional.empty());
    for (String code : new String[] {"OFF", "NOPE"}) {
      assertRejected(
          new TimeEntryCommand(Granularity.DAY, D, null, null, null, code, null, null),
          "RG-01-04",
          "activityTypeCode");
    }
  }

  @Test
  @DisplayName("CA-01-05 : RG-01-05, projet obligatoire pour un type qui l'exige")
  void projectRequired() {
    assertRejected(
        new TimeEntryCommand(Granularity.DAY, D, null, null, null, "TRAVAIL_PROJET", null, null),
        "RG-01-05",
        "projectCode");
    TimeEntry e =
        service.create(
            USER,
            new TimeEntryCommand(
                Granularity.DAY, D, null, null, null, "TRAVAIL_PROJET", "P1", null));
    assertThat(e.getProject()).isSameAs(project);
  }

  @Test
  @DisplayName("RG-01-05 : projet interdit pour un type qui ne l'exige pas")
  void projectForbidden() {
    assertRejected(
        new TimeEntryCommand(Granularity.DAY, D, null, null, null, "REUNION", "P1", null),
        "RG-01-05",
        "projectCode");
  }

  @Test
  @DisplayName("RG-02-06 : projet inactif ou inconnu refusé sur projectCode")
  void projectMustBeActive() {
    Project off = new Project("OFF", "Off");
    off.setActive(false);
    when(projects.findByCode("OFF")).thenReturn(Optional.of(off));
    when(projects.findByCode("NOPE")).thenReturn(Optional.empty());
    for (String code : new String[] {"OFF", "NOPE"}) {
      assertRejected(
          new TimeEntryCommand(Granularity.DAY, D, null, null, null, "TRAVAIL_PROJET", code, null),
          "RG-02-06",
          "projectCode");
    }
  }

  @Test
  @DisplayName("CA-06-03 : RG-06-04, la journée standard en vigueur (450) est lue et figée")
  void standardDayReadAtSaveAndFrozen() {
    when(settings.getStandardDayMinutes()).thenReturn(450);
    TimeEntry e = service.create(USER, day(D));
    assertThat(e.getDurationMinutes()).isEqualTo(450);
    assertThat(e.getStandardDayMinutes()).isEqualTo(450);
    verify(settings).getStandardDayMinutes();
  }

  @Test
  @DisplayName("RG-01-09 : le commentaire est conservé tel quel")
  void commentKept() {
    TimeEntry e =
        service.create(
            USER,
            new TimeEntryCommand(Granularity.DAY, D, null, null, null, "REUNION", null, "note"));
    assertThat(e.getComment()).isEqualTo("note");
  }

  @Test
  @DisplayName("RG-01-10 : lire la saisie d'un autre utilisateur donne 403 RG-01-10")
  void otherUsersEntryForbidden() {
    TimeEntry e = new TimeEntry(8L, meeting);
    when(entries.findActiveById(1L)).thenReturn(Optional.of(e));
    assertThatThrownBy(() -> service.getOwn(USER, 1L))
        .isInstanceOfSatisfying(
            ForbiddenOperationException.class,
            ex -> assertThat(ex.getRgCode()).isEqualTo("RG-01-10"));
  }

  @Test
  @DisplayName("RG-01-11 : saisie absente ou supprimée logiquement = introuvable")
  void missingOrDeletedIsNotFound() {
    when(entries.findActiveById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getOwn(USER, 1L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("RG-00-03 : le propriétaire lit sa propre saisie")
  void ownerReadsOwnEntry() {
    TimeEntry e = new TimeEntry(USER, meeting);
    when(entries.findActiveById(1L)).thenReturn(Optional.of(e));
    assertThat(service.getOwn(USER, 1L)).isSameAs(e);
  }

  @Test
  @DisplayName("CA-01-04 : RG-01-06, un chevauchement donne 409 RG-01-06 avec la date du conflit")
  void overlapRejected() {
    when(entries.findFirstOverlapDay(any(), any(), any(), anyLong()))
        .thenReturn(List.of("2026-10-05"));
    TimeEntryCommand cmd = hour(LocalTime.of(9, 30), LocalTime.of(10, 30));
    clearInvocations(entries);
    assertThatThrownBy(() -> service.create(USER, cmd))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getRgCode()).isEqualTo("RG-01-06");
              assertThat(e.getMessage()).contains("05/10/2026").contains("chevauche");
            });
    verify(entries, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("RG-01-06 : HOUR est contrôlé sur [début, fin), début inclus et fin exclue")
  void hourIntervalIsHalfOpen() {
    service.create(USER, hour(LocalTime.of(9, 0), LocalTime.of(10, 0)));
    verify(entries).findFirstOverlapDay(USER, D.atTime(9, 0), D.atTime(10, 0), -1L);
  }

  @Test
  @DisplayName("RG-01-06 : JOURNEE et PLAGE occupent des journées entières, week-ends compris")
  void dayAndRangeOccupyWholeDays() {
    service.create(USER, day(D));
    verify(entries).findFirstOverlapDay(USER, D.atStartOfDay(), D.plusDays(1).atStartOfDay(), -1L);
    service.create(USER, range(D, D.plusDays(6)));
    verify(entries).findFirstOverlapDay(USER, D.atStartOfDay(), D.plusDays(7).atStartOfDay(), -1L);
  }

  private TimeEntry existing(Long owner) {
    TimeEntry e = new TimeEntry(owner, meeting);
    e.setPeriod(Granularity.HOUR, D, D, LocalTime.of(9, 0), LocalTime.of(10, 0));
    e.setDuration(60, 420);
    return e;
  }

  @Test
  @DisplayName("RG-01-12 : la modification applique tous les champs, granularité comprise")
  void updateChangesGranularityAndClearsTimes() {
    TimeEntry e = existing(USER);
    when(entries.findActiveById(5L)).thenReturn(Optional.of(e));
    TimeEntry updated = service.update(USER, 5L, 0L, range(D, D.plusDays(4)));
    assertThat(updated.getGranularity()).isEqualTo(Granularity.RANGE);
    assertThat(updated.getEndDate()).isEqualTo(D.plusDays(4));
    assertThat(updated.getStartTime()).isNull(); // champs sans objet remis à vide
    assertThat(updated.getEndTime()).isNull();
    assertThat(updated.getDurationMinutes()).isEqualTo(5 * 420);
    TimeEntry back = service.update(USER, 5L, 0L, hour(LocalTime.of(14, 0), LocalTime.of(15, 0)));
    assertThat(back.getEndDate()).isEqualTo(D);
    assertThat(back.getStartTime()).isEqualTo(LocalTime.of(14, 0));
    assertThat(back.getDurationMinutes()).isEqualTo(60);
  }

  @Test
  @DisplayName("RG-01-12 : le contrôle de chevauchement ignore la saisie modifiée elle-même")
  void updateExcludesItself() {
    when(entries.findActiveById(5L)).thenReturn(Optional.of(existing(USER)));
    service.update(USER, 5L, 0L, hour(LocalTime.of(9, 30), LocalTime.of(10, 30)));
    verify(entries).findFirstOverlapDay(USER, D.atTime(9, 30), D.atTime(10, 30), 5L);
  }

  @Test
  @DisplayName("CA-01-08 : RG-01-10, modifier la saisie d'un autre utilisateur donne 403 RG-01-10")
  void updateOtherUsersEntryForbidden() {
    when(entries.findActiveById(5L)).thenReturn(Optional.of(existing(8L)));
    assertThatThrownBy(
            () -> service.update(USER, 5L, 0L, hour(LocalTime.of(9, 0), LocalTime.of(10, 0))))
        .isInstanceOfSatisfying(
            ForbiddenOperationException.class,
            ex -> assertThat(ex.getRgCode()).isEqualTo("RG-01-10"));
    verify(entries, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("RG-01-11 : modifier une saisie absente ou supprimée donne 404")
  void updateMissingIsNotFound() {
    when(entries.findActiveById(5L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.update(USER, 5L, 0L, day(D)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("RG-01-12 : une version périmée donne un conflit de version, rien n'est écrit")
  void updateStaleVersion() {
    when(entries.findActiveById(5L)).thenReturn(Optional.of(existing(USER)));
    assertThatThrownBy(() -> service.update(USER, 5L, 3L, day(D)))
        .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    verify(entries, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("RG-06-04 : la modification refige durée et journée standard en vigueur")
  void updateRefreezesStandardDay() {
    TimeEntry e = existing(USER);
    e.setPeriod(Granularity.DAY, D, D, null, null);
    e.setDuration(420, 420);
    when(entries.findActiveById(5L)).thenReturn(Optional.of(e));
    when(settings.getStandardDayMinutes()).thenReturn(450);
    TimeEntry updated = service.update(USER, 5L, 0L, day(D));
    assertThat(updated.getDurationMinutes()).isEqualTo(450);
    assertThat(updated.getStandardDayMinutes()).isEqualTo(450);
  }

  @Test
  @DisplayName("RG-02-06 : à la modification, un type devenu inactif est refusé même inchangé")
  void updateRejectsInactiveType() {
    TimeEntry e = existing(USER);
    when(entries.findActiveById(5L)).thenReturn(Optional.of(e));
    meeting.setActive(false);
    assertThatThrownBy(() -> service.update(USER, 5L, 0L, day(D)))
        .isInstanceOfSatisfying(
            BusinessException.class, ex -> assertThat(ex.getRgCode()).isEqualTo("RG-01-04"));
    verify(entries, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("RG-02-06 : à la modification, un projet devenu inactif est refusé")
  void updateRejectsInactiveProject() {
    TimeEntry e = existing(USER);
    when(entries.findActiveById(5L)).thenReturn(Optional.of(e));
    project.setActive(false);
    TimeEntryCommand cmd =
        new TimeEntryCommand(Granularity.DAY, D, null, null, null, "TRAVAIL_PROJET", "P1", null);
    assertThatThrownBy(() -> service.update(USER, 5L, 0L, cmd))
        .isInstanceOfSatisfying(
            BusinessException.class, ex -> assertThat(ex.getRgCode()).isEqualTo("RG-02-06"));
  }

  @Test
  @DisplayName("RG-01-11 : la suppression est logique, qui et quand (horloge applicative)")
  void deleteIsLogical() {
    TimeEntry e = existing(USER);
    when(entries.findActiveById(5L)).thenReturn(Optional.of(e));
    service.delete(USER, 5L);
    assertThat(e.isDeleted()).isTrue();
    assertThat(e.getDeletedBy()).isEqualTo(USER);
    assertThat(e.getDeletedAt()).isEqualTo(CLOCK.instant());
    verify(entries).saveAndFlush(e);
  }

  @Test
  @DisplayName("CA-01-08 : RG-01-10, supprimer la saisie d'autrui donne 403 RG-01-10")
  void deleteOtherUsersEntryForbidden() {
    TimeEntry e = existing(8L);
    when(entries.findActiveById(5L)).thenReturn(Optional.of(e));
    assertThatThrownBy(() -> service.delete(USER, 5L))
        .isInstanceOfSatisfying(
            ForbiddenOperationException.class,
            ex -> assertThat(ex.getRgCode()).isEqualTo("RG-01-10"));
    assertThat(e.isDeleted()).isFalse();
    verify(entries, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("RG-01-11 : supprimer une saisie absente ou déjà supprimée donne 404")
  void deleteMissingIsNotFound() {
    when(entries.findActiveById(5L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.delete(USER, 5L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("RG-01-13 : weekStart doit être un lundi, sinon 400 sur weekStart")
  void listWeekRequiresMonday() {
    assertThatThrownBy(() -> service.listWeek(USER, LocalDate.of(2026, 10, 6)))
        .isInstanceOfSatisfying(
            com.apptracer.common.error.InvalidRequestException.class,
            ex -> assertThat(ex.getErrors()).extracting("field").containsExactly("weekStart"));
    assertThatThrownBy(() -> service.listWeek(USER, LocalDate.of(2026, 10, 11)))
        .isInstanceOf(com.apptracer.common.error.InvalidRequestException.class);
  }

  @Test
  @DisplayName("CA-01-09 : RG-00-03, la semaine est lue pour le compte donné, total = somme")
  void listWeekUsesGivenUser() {
    TimeEntry a = existing(USER); // 60 min le 05/10
    TimeEntry b = existing(USER);
    b.setDuration(90 - 60 + 60, 420);
    when(reader.read(USER, D)).thenReturn(List.of(a, b));
    var week = service.listWeek(USER, D);
    assertThat(week.weekEnd()).isEqualTo(D.plusDays(6));
    assertThat(week.totalMinutes()).isEqualTo(60 + 90);
    assertThat(week.entries()).containsExactly(a, b);
  }
}
