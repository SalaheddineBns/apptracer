package com.apptracer.teamview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.common.error.ForbiddenOperationException;
import com.apptracer.common.error.InvalidRequestException;
import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.domain.TagColor;
import com.apptracer.team.domain.Team;
import com.apptracer.team.domain.TeamMember;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import com.apptracer.teamview.service.ManagedScope;
import com.apptracer.teamview.service.TeamViewService;
import com.apptracer.teamview.service.TeamViewService.MemberJournal;
import com.apptracer.timeentry.domain.Granularity;
import com.apptracer.timeentry.domain.TimeEntry;
import com.apptracer.timeentry.service.WeeklyEntriesReader;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeamMemberJournalServiceTest {

  private static final LocalDate WEEK = LocalDate.of(2026, 10, 5); // lundi
  private static final ActivityType TYPE = new ActivityType("REUNION", "R", false, TagColor.WARN);

  private final TeamRepository teams = mock(TeamRepository.class);
  private final TeamMemberRepository members = mock(TeamMemberRepository.class);
  private final WeeklyEntriesReader reader = mock(WeeklyEntriesReader.class);
  private final TeamViewService service =
      new TeamViewService(new ManagedScope(teams, members), members, reader);

  private static void set(Object target, String name, Object value) {
    try {
      var f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static User account(Long id, boolean active) {
    User u = new User("u" + id + "@example.com", "F" + id, "L" + id, "hash", Role.USER);
    u.setActive(active);
    set(u, "id", id);
    return u;
  }

  private static TeamMember link(User user, Team team) {
    TeamMember m = new TeamMember(user.getId(), team.getId());
    set(m, "user", user);
    set(m, "team", team);
    return m;
  }

  private static TimeEntry hour(Long userId, LocalDate d, int minutes) {
    TimeEntry e = new TimeEntry(userId, TYPE);
    e.setPeriod(Granularity.HOUR, d, d, LocalTime.of(9, 0), LocalTime.of(10, 0));
    e.setDuration(minutes, 420);
    return e;
  }

  private final Team alpha = new Team("Alpha", account(1L, true));

  @Test
  @DisplayName("CA-04-03 / RG-04-02 : un compte hors des équipes du manager donne 403 RG-04-02")
  void accountOutsideTheScopeIsForbidden() {
    when(members.findManagedMember(5L, 1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.listTeamMemberTimeEntries(1L, 5L, WEEK))
        .isInstanceOfSatisfying(
            ForbiddenOperationException.class,
            e -> assertThat(e.getRgCode()).isEqualTo("RG-04-02"));
    verify(reader, never()).read(anyLong(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("RG-04-04 : une semaine qui ne commence pas un lundi est refusée (400)")
  void weekMustStartOnMonday() {
    User anne = account(2L, true);
    when(members.findManagedMember(2L, 1L)).thenReturn(Optional.of(link(anne, alpha)));
    assertThatThrownBy(() -> service.listTeamMemberTimeEntries(1L, 2L, WEEK.plusDays(2)))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @DisplayName("RG-04-05 : saisies et total viennent de WeeklyEntriesReader et du calculateur")
  void entriesAndTotalAreThoseOfTheMemberJournal() {
    User anne = account(2L, true);
    when(members.findManagedMember(2L, 1L)).thenReturn(Optional.of(link(anne, alpha)));
    List<TimeEntry> entries = List.of(hour(2L, WEEK, 90), hour(2L, WEEK.plusDays(1), 420));
    when(reader.read(2L, WEEK)).thenReturn(entries);

    MemberJournal journal = service.listTeamMemberTimeEntries(1L, 2L, WEEK);

    assertThat(journal.week().entries()).isEqualTo(entries);
    assertThat(journal.week().totalMinutes()).isEqualTo(510);
    assertThat(journal.week().weekStart()).isEqualTo(WEEK);
    assertThat(journal.week().weekEnd()).isEqualTo(WEEK.plusDays(6));
    assertThat(journal.team()).isSameAs(alpha);
    assertThat(journal.member()).isSameAs(anne);
  }

  @Test
  @DisplayName("CA-04-12 / RG-04-07 : le journal d'un membre inactif reste consultable")
  void inactiveMemberJournalIsReadable() {
    User anne = account(2L, false);
    when(members.findManagedMember(2L, 1L)).thenReturn(Optional.of(link(anne, alpha)));
    when(reader.read(2L, WEEK)).thenReturn(List.of(hour(2L, WEEK, 60)));

    MemberJournal journal = service.listTeamMemberTimeEntries(1L, 2L, WEEK);

    assertThat(journal.member().isActive()).isFalse();
    assertThat(journal.week().totalMinutes()).isEqualTo(60);
  }
}
