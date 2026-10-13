package com.apptracer.timeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.domain.Project;
import com.apptracer.referential.repository.ActivityTypeRepository;
import com.apptracer.referential.repository.ProjectRepository;
import com.apptracer.timeentry.domain.Granularity;
import com.apptracer.timeentry.domain.TimeEntry;
import com.apptracer.timeentry.repository.TimeEntryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TimeEntryPersistenceTest {

  private static final LocalDate D = LocalDate.of(2026, 10, 5);

  @Autowired private TimeEntryRepository entries;
  @Autowired private ActivityTypeRepository types;
  @Autowired private ProjectRepository projects;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private User owner;
  private ActivityType type;

  @BeforeEach
  void setUp() {
    owner = users.saveAndFlush(new User("te-owner@example.com", "A", "B", "hash", Role.USER));
    login(owner);
    type = types.findByCode("REUNION").orElseThrow();
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    // saisies avant comptes, projets de test avant comptes ; types initiaux conservés
    jdbc.execute("DELETE FROM time_entries");
    jdbc.execute("DELETE FROM projects");
    jdbc.execute("DELETE FROM users WHERE email = 'te-owner@example.com'");
  }

  private void login(User u) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(String.valueOf(u.getId()), null, List.of()));
  }

  private TimeEntry entry(
      Granularity g, LocalDate start, LocalDate end, LocalTime from, LocalTime to) {
    TimeEntry e = new TimeEntry(owner.getId(), type);
    e.setPeriod(g, start, end, from, to);
    e.setDuration(60, 420);
    return e;
  }

  private TimeEntry hour(LocalTime from, LocalTime to) {
    return entry(Granularity.HOUR, D, D, from, to);
  }

  private TimeEntry range(LocalDate start, LocalDate end) {
    return entry(Granularity.RANGE, start, end, null, null);
  }

  private void rejected(TimeEntry e) {
    assertThatThrownBy(() -> entries.saveAndFlush(e))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("RG-01-01 : une saisie HOUR valide est enregistrée, fin au plus tard 23:45")
  void hourAccepted() {
    TimeEntry saved = entries.saveAndFlush(hour(LocalTime.of(9, 0), LocalTime.of(10, 30)));
    TimeEntry read = entries.findById(saved.getId()).orElseThrow();
    assertThat(read.getGranularity()).isEqualTo(Granularity.HOUR);
    assertThat(read.getStartTime()).isEqualTo(LocalTime.of(9, 0));
    assertThat(read.getEndTime()).isEqualTo(LocalTime.of(10, 30));
    assertThat(read.getDurationMinutes()).isEqualTo(60);
    assertThat(read.getStandardDayMinutes()).isEqualTo(420);
    entries.saveAndFlush(hour(LocalTime.of(23, 0), LocalTime.of(23, 45)));
  }

  @Test
  @DisplayName("RG-01-01 : HOUR refusée si fin égale ou antérieure au début")
  void hourEndNotAfterStartRejected() {
    rejected(hour(LocalTime.of(10, 0), LocalTime.of(10, 0)));
    rejected(hour(LocalTime.of(10, 0), LocalTime.of(9, 0)));
  }

  @Test
  @DisplayName("RG-01-01 : HOUR refusée si les heures ne sont pas des multiples de 15 minutes")
  void hourNotQuarterRejected() {
    rejected(hour(LocalTime.of(9, 10), LocalTime.of(10, 0)));
    rejected(hour(LocalTime.of(9, 0), LocalTime.of(10, 5)));
    rejected(hour(LocalTime.of(9, 0), LocalTime.of(10, 0, 30)));
    entries.saveAndFlush(hour(LocalTime.of(9, 15), LocalTime.of(9, 30)));
  }

  @Test
  @DisplayName("RG-01-01 : HOUR refusée sans heure de début ou sans heure de fin")
  void hourWithoutTimesRejected() {
    rejected(hour(null, null));
    rejected(hour(LocalTime.of(9, 0), null));
    rejected(hour(null, LocalTime.of(10, 0)));
  }

  @Test
  @DisplayName("RG-01-02 : DAY couvre la journée de startDate, sans heures et avec endDate égale")
  void dayRules() {
    entries.saveAndFlush(entry(Granularity.DAY, D, D, null, null));
    rejected(entry(Granularity.DAY, D, D.plusDays(1), null, null));
    rejected(entry(Granularity.DAY, D, D, LocalTime.of(9, 0), LocalTime.of(10, 0)));
  }

  @Test
  @DisplayName("RG-01-02 : une saisie HOUR ne peut pas s'étendre sur plusieurs dates")
  void hourSingleDateOnly() {
    rejected(entry(Granularity.HOUR, D, D.plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0)));
  }

  @Test
  @DisplayName("RG-01-03 : RANGE de 2 jours et de 31 jours acceptées")
  void rangeBoundsAccepted() {
    entries.saveAndFlush(range(D, D.plusDays(1)));
    entries.saveAndFlush(range(D.plusDays(10), D.plusDays(10).plusDays(30)));
  }

  @Test
  @DisplayName("RG-01-03 : RANGE d'un seul jour, de 32 jours ou inversée refusées")
  void rangeOutOfBoundsRejected() {
    rejected(range(D, D));
    rejected(range(D, D.plusDays(31)));
    rejected(range(D, D.minusDays(1)));
  }

  @Test
  @DisplayName("RG-01-03 : RANGE refusée si elle porte des heures")
  void rangeWithTimesRejected() {
    rejected(entry(Granularity.RANGE, D, D.plusDays(2), LocalTime.of(9, 0), LocalTime.of(10, 0)));
  }

  @Test
  @DisplayName("RG-01-09 : commentaire de 500 caractères accepté, de 501 refusé")
  void commentLength() {
    TimeEntry ok = hour(LocalTime.of(9, 0), LocalTime.of(10, 0));
    ok.setComment("x".repeat(500));
    entries.saveAndFlush(ok);
    TimeEntry ko = hour(LocalTime.of(11, 0), LocalTime.of(12, 0));
    ko.setComment("x".repeat(501));
    rejected(ko);
  }

  @Test
  @DisplayName("RG-01-08 : durée négative ou journée standard nulle refusées par la base")
  void frozenDurationChecks() {
    TimeEntry negative = hour(LocalTime.of(9, 0), LocalTime.of(10, 0));
    negative.setDuration(-1, 420);
    rejected(negative);
    TimeEntry noStandard = hour(LocalTime.of(9, 0), LocalTime.of(10, 0));
    noStandard.setDuration(0, 0);
    rejected(noStandard);
    TimeEntry zeroDuration = range(D, D.plusDays(1)); // plage sans jour ouvré : 0 min
    zeroDuration.setDuration(0, 420);
    entries.saveAndFlush(zeroDuration);
  }

  @Test
  @DisplayName("RG-01-11, RG-00-04 : créé par / le et modifié par / le sont alimentés par l'audit")
  void auditColumns() {
    TimeEntry saved = entries.saveAndFlush(hour(LocalTime.of(9, 0), LocalTime.of(10, 0)));
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
    assertThat(saved.getCreatedBy()).isEqualTo(owner.getId());
    assertThat(saved.getUpdatedBy()).isEqualTo(owner.getId());
    assertThat(saved.getDeletedAt()).isNull();

    User other =
        users.saveAndFlush(new User("te-owner@example.com.x", "C", "D", "h", Role.MANAGER));
    try {
      login(other);
      saved.setComment("modifié");
      TimeEntry updated = entries.saveAndFlush(saved);
      assertThat(updated.getCreatedBy()).isEqualTo(owner.getId());
      assertThat(updated.getUpdatedBy()).isEqualTo(other.getId());
      assertThat(updated.getVersion()).isEqualTo(1L);
    } finally {
      jdbc.execute("DELETE FROM time_entries");
      jdbc.execute("DELETE FROM users WHERE email = 'te-owner@example.com.x'");
    }
  }

  @Test
  @DisplayName("RG-01-11 : la création sans auteur est refusée (created_by obligatoire)")
  void creationWithoutAuthorRejected() {
    SecurityContextHolder.clearContext();
    rejected(hour(LocalTime.of(9, 0), LocalTime.of(10, 0)));
  }

  @Test
  @DisplayName("RG-01-11 : suppression logique, qui et quand renseignés ensemble")
  void softDelete() {
    TimeEntry saved = entries.saveAndFlush(hour(LocalTime.of(9, 0), LocalTime.of(10, 0)));
    saved.markDeleted(owner.getId(), Instant.parse("2026-10-06T08:00:00Z"));
    TimeEntry deleted = entries.saveAndFlush(saved);
    assertThat(deleted.isDeleted()).isTrue();
    assertThat(deleted.getDeletedBy()).isEqualTo(owner.getId());
    assertThat(entries.findById(saved.getId())).isPresent(); // la ligne reste en base

    Long id = saved.getId();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE time_entries SET deleted_at = now(), deleted_by = NULL WHERE id = ?",
                    id))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE time_entries SET deleted_at = NULL, deleted_by = ? WHERE id = ?",
                    owner.getId(),
                    id))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("RG-01-04, RG-01-05 : clés étrangères vers le type et le projet (facultatif)")
  void foreignKeys() {
    Project p = projects.saveAndFlush(new Project("TE_P1", "Projet"));
    TimeEntry withProject = hour(LocalTime.of(9, 0), LocalTime.of(10, 0));
    withProject.setProject(p);
    TimeEntry saved = entries.saveAndFlush(withProject);
    assertThat(saved.getProject().getId()).isEqualTo(p.getId());
    assertThat(saved.getActivityType().getCode()).isEqualTo("REUNION");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE time_entries SET project_id = 999999 WHERE id = ?", saved.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM projects WHERE id = ?", p.getId()))
        .isInstanceOf(DataIntegrityViolationException.class); // pas de ON DELETE
  }

  @Test
  @DisplayName("RG-01-10 : la base refuse une granularité inconnue et un compte inexistant")
  void unknownGranularityAndUserRejected() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO time_entries (user_id, granularity, start_date, end_date,"
                        + " activity_type_id, duration_minutes, standard_day_minutes, created_at,"
                        + " created_by, updated_at, updated_by) VALUES (?, 'WEEK', ?, ?, ?, 0, 420,"
                        + " now(), ?, now(), ?)",
                    owner.getId(),
                    D,
                    D,
                    type.getId(),
                    owner.getId(),
                    owner.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
    TimeEntry orphan = new TimeEntry(999_999L, type);
    orphan.setPeriod(Granularity.DAY, D, D, null, null);
    orphan.setDuration(420, 420);
    rejected(orphan);
  }
}
