package com.apptracer.timeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.repository.ActivityTypeRepository;
import com.apptracer.security.AccessTokenService;
import com.apptracer.timeentry.domain.Granularity;
import com.apptracer.timeentry.domain.TimeEntry;
import com.apptracer.timeentry.repository.TimeEntryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** RG-01-06: no overlap, service check and SQL exclusion constraint. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TimeEntryOverlapTest {

  private static final LocalDate MON = LocalDate.of(2026, 10, 5);

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private ActivityTypeRepository types;
  @MockitoSpyBean private TimeEntryRepository entries;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User user;
  private User other;
  private ActivityType type;

  @BeforeEach
  void setUp() {
    user = users.save(new User("ov-user@example.com", "U", "User", "hash", Role.USER));
    other = users.save(new User("ov-other@example.com", "O", "Other", "hash", Role.MANAGER));
    type = types.findByCode("REUNION").orElseThrow();
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    jdbc.execute("DELETE FROM time_entries");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users");
  }

  private ResultActions send(User u, String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/time-entries")
            .header(HttpHeaders.ACCEPT_LANGUAGE, "fr")
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + tokens.issue(u.getId(), u.getRole(), UUID.randomUUID()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private static String hour(String date, String from, String to) {
    return "{\"granularity\":\"HOUR\",\"startDate\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\",\"activityTypeCode\":\"REUNION\"}"
        .formatted(date, from, to);
  }

  private static String day(String date) {
    return "{\"granularity\":\"DAY\",\"startDate\":\"%s\",\"activityTypeCode\":\"REUNION\"}"
        .formatted(date);
  }

  private static String range(String from, String to) {
    return "{\"granularity\":\"RANGE\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"activityTypeCode\":\"REUNION\"}"
        .formatted(from, to);
  }

  private void conflict(User u, String body, String dateInMessage) throws Exception {
    send(u, body)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-01-06"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(dateInMessage)));
  }

  private void login(User u) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(String.valueOf(u.getId()), null, List.of()));
  }

  private TimeEntry entry(
      User owner, Granularity g, LocalDate start, LocalDate end, LocalTime from, LocalTime to) {
    TimeEntry e = new TimeEntry(owner.getId(), type);
    e.setPeriod(g, start, end, from, to);
    e.setDuration(60, 420);
    return e;
  }

  @Test
  @DisplayName("CA-01-04 : 09:30-10:30 sur une saisie 09:00-10:00 donne 409 RG-01-06 avec la date")
  void overlappingHourRejected() throws Exception {
    send(user, hour("2026-10-05", "09:00", "10:00")).andExpect(status().isCreated());
    conflict(user, hour("2026-10-05", "09:30", "10:30"), "05/10/2026");
    assertThat(entries.count()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "RG-01-06 : créneaux contigus 09:00-10:00 puis 10:00-11:00 acceptés, dans les deux sens")
  void adjacentSlotsAccepted() throws Exception {
    send(user, hour("2026-10-05", "10:00", "11:00")).andExpect(status().isCreated());
    send(user, hour("2026-10-05", "09:00", "10:00")).andExpect(status().isCreated());
    send(user, hour("2026-10-05", "11:00", "11:15")).andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "RG-01-06 : 15 minutes de recouvrement refusées, créneau identique et inclus refusés")
  void smallestOverlapRejected() throws Exception {
    send(user, hour("2026-10-05", "09:00", "10:00")).andExpect(status().isCreated());
    conflict(user, hour("2026-10-05", "08:00", "09:15"), "05/10/2026");
    conflict(user, hour("2026-10-05", "09:45", "10:45"), "05/10/2026");
    conflict(user, hour("2026-10-05", "09:00", "10:00"), "05/10/2026");
    conflict(user, hour("2026-10-05", "09:15", "09:30"), "05/10/2026");
  }

  @Test
  @DisplayName("RG-01-06 : le même créneau pour deux utilisateurs différents est accepté")
  void otherUserNotBlocked() throws Exception {
    send(user, hour("2026-10-05", "09:00", "10:00")).andExpect(status().isCreated());
    send(other, hour("2026-10-05", "09:00", "10:00")).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("RG-01-06 : une JOURNEE exclut tout créneau HOUR et toute autre saisie du jour")
  void dayExcludesEverythingThatDay() throws Exception {
    send(user, day("2026-10-05")).andExpect(status().isCreated());
    conflict(user, hour("2026-10-05", "09:00", "10:00"), "05/10/2026");
    conflict(user, day("2026-10-05"), "05/10/2026");
    send(user, hour("2026-10-06", "09:00", "10:00")).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("RG-01-06 : un créneau HOUR exclut une JOURNEE du même jour")
  void hourExcludesDay() throws Exception {
    send(user, hour("2026-10-05", "09:00", "10:00")).andExpect(status().isCreated());
    conflict(user, day("2026-10-05"), "05/10/2026");
  }

  @Test
  @DisplayName("RG-01-06 : PLAGE contre JOURNEE, chevauchement refusé avec le premier jour commun")
  void rangeAgainstDay() throws Exception {
    send(user, day("2026-10-08")).andExpect(status().isCreated());
    conflict(user, range("2026-10-05", "2026-10-09"), "08/10/2026");
    // bounds: a range ending the day before or starting the day after is accepted
    send(user, range("2026-10-05", "2026-10-07")).andExpect(status().isCreated());
    send(user, range("2026-10-09", "2026-10-12")).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("RG-01-06 : une PLAGE occupe les week-ends, une JOURNEE de samedi est refusée")
  void rangeOccupiesWeekends() throws Exception {
    send(user, range("2026-10-05", "2026-10-12")).andExpect(status().isCreated());
    conflict(user, day("2026-10-10"), "10/10/2026"); // samedi
    conflict(user, hour("2026-10-11", "09:00", "10:00"), "11/10/2026"); // dimanche
    // the first range covers the 12th: a range touching it by its last day is refused
    conflict(user, range("2026-10-12", "2026-10-14"), "12/10/2026");
    send(user, range("2026-10-13", "2026-10-14")).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("RG-01-06 : PLAGE contre PLAGE qui se recoupent partiellement est refusée")
  void rangeAgainstRange() throws Exception {
    send(user, range("2026-10-05", "2026-10-09")).andExpect(status().isCreated());
    conflict(user, range("2026-10-08", "2026-10-14"), "08/10/2026");
    conflict(user, range("2026-10-01", "2026-10-05"), "05/10/2026");
  }

  @Test
  @DisplayName("RG-01-11 : une saisie supprimée logiquement ne bloque plus le créneau")
  void deletedEntryIgnored() throws Exception {
    send(user, hour("2026-10-05", "09:00", "10:00")).andExpect(status().isCreated());
    TimeEntry e = entries.findAll().get(0);
    e.markDeleted(user.getId(), Instant.now());
    entries.saveAndFlush(e);
    send(user, hour("2026-10-05", "09:00", "10:00")).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("CA-01-04 : deux requêtes simultanées, la contrainte SQL donne aussi 409 RG-01-06")
  void raceOnExclusionConstraintIs409() throws Exception {
    send(user, hour("2026-10-05", "09:00", "10:00")).andExpect(status().isCreated());
    // the service check does not see the conflict (race): the database refuses it
    Mockito.doReturn(List.of()).when(entries).findFirstOverlapDay(any(), any(), any(), anyLong());
    send(user, hour("2026-10-05", "09:30", "10:30"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RG-01-06"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("chevauche")));
    assertThat(entries.count()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "RG-01-06 : la contrainte d'exclusion refuse directement les saisies qui se recoupent")
  void exclusionConstraintFiresOnRepository() {
    login(user);
    entries.saveAndFlush(
        entry(user, Granularity.HOUR, MON, MON, LocalTime.of(9, 0), LocalTime.of(10, 0)));
    // adjacent: accepted
    entries.saveAndFlush(
        entry(user, Granularity.HOUR, MON, MON, LocalTime.of(10, 0), LocalTime.of(11, 0)));
    // 15-minute overlap: refused
    assertThatThrownBy(
            () ->
                entries.saveAndFlush(
                    entry(
                        user,
                        Granularity.HOUR,
                        MON,
                        MON,
                        LocalTime.of(10, 45),
                        LocalTime.of(11, 15))))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ex_time_entries_no_overlap");
    // DAY on the same day: refused
    assertThatThrownBy(
            () -> entries.saveAndFlush(entry(user, Granularity.DAY, MON, MON, null, null)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ex_time_entries_no_overlap");
    // another user: accepted
    login(other);
    entries.saveAndFlush(
        entry(other, Granularity.HOUR, MON, MON, LocalTime.of(9, 0), LocalTime.of(10, 0)));
  }

  @Test
  @DisplayName(
      "RG-01-11 : la contrainte d'exclusion est partielle, une saisie supprimée est exclue")
  void exclusionConstraintIgnoresDeleted() {
    login(user);
    TimeEntry first = entries.saveAndFlush(entry(user, Granularity.DAY, MON, MON, null, null));
    first.markDeleted(user.getId(), Instant.now());
    entries.saveAndFlush(first);
    entries.saveAndFlush(entry(user, Granularity.DAY, MON, MON, null, null));
    assertThat(entries.count()).isEqualTo(2);
  }
}
