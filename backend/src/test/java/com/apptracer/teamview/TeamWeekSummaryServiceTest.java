package com.apptracer.teamview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
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
import com.apptracer.teamview.service.TeamViewService.TeamWeek;
import com.apptracer.timeentry.domain.Granularity;
import com.apptracer.timeentry.domain.TimeEntry;
import com.apptracer.timeentry.service.WeeklyEntriesReader;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeamWeekSummaryServiceTest {

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

  private static User account(Long id, String first, String last, Role role, boolean active) {
    User u = new User("u" + id + "@example.com", first, last, "hash", role);
    u.setActive(active);
    set(u, "id", id);
    return u;
  }

  private static Team team(Long id, User manager) {
    Team t = new Team("Alpha", manager);
    set(t, "id", id);
    return t;
  }

  private static TeamMember link(User user, Team team) {
    TeamMember m = new TeamMember(user.getId(), team.getId());
    set(m, "user", user);
    return m;
  }

  private static TimeEntry hour(Long userId, LocalDate d, int minutes) {
    TimeEntry e = new TimeEntry(userId, TYPE);
    e.setPeriod(
        Granularity.HOUR, d, d, java.time.LocalTime.of(9, 0), java.time.LocalTime.of(10, 0));
    e.setDuration(minutes, 480);
    return e;
  }

  private static TimeEntry range(Long userId, LocalDate from, LocalDate to, int std) {
    TimeEntry e = new TimeEntry(userId, TYPE);
    e.setPeriod(Granularity.RANGE, from, to, null, null);
    e.setDuration(0, std);
    return e;
  }

  private final User manager = account(1L, "Mia", "Manager", Role.MANAGER, true);
  private final Team alpha = team(10L, manager);

  private void managerOfAlpha() {
    when(teams.findActiveById(10L)).thenReturn(Optional.of(alpha));
  }

  @Test
  @DisplayName("RG-04-02 : une équipe inconnue ou supprimée donne 403 RG-04-02, comme une autre")
  void unknownTeamIsForbidden() {
    when(teams.findActiveById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getTeamWeekSummary(1L, 99L, WEEK))
        .isInstanceOfSatisfying(
            ForbiddenOperationException.class,
            e -> assertThat(e.getRgCode()).isEqualTo("RG-04-02"));
    verify(members, never()).findByTeamIdsSorted(anyCollection());
  }

  @Test
  @DisplayName("CA-04-03 / RG-04-02 : l'équipe d'un autre manager donne 403 RG-04-02")
  void otherManagersTeamIsForbidden() {
    managerOfAlpha();
    assertThatThrownBy(() -> service.getTeamWeekSummary(2L, 10L, WEEK))
        .isInstanceOfSatisfying(
            ForbiddenOperationException.class,
            e -> assertThat(e.getRgCode()).isEqualTo("RG-04-02"));
    verify(reader, never()).readAll(anyCollection(), eq(WEEK));
  }

  @Test
  @DisplayName("RG-04-04 : une semaine qui ne commence pas un lundi est refusée (400)")
  void weekMustStartOnMonday() {
    managerOfAlpha();
    assertThatThrownBy(() -> service.getTeamWeekSummary(1L, 10L, WEEK.plusDays(1)))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @DisplayName(
      "CA-04-01 / RG-04-05 : durée par jour et total viennent du calculateur, un membre sans saisie à 0")
  void totalsComeFromTheCalculator() {
    User anne = account(2L, "Anne", "Adam", Role.USER, true);
    User paul = account(3L, "Paul", "Petit", Role.USER, true);
    managerOfAlpha();
    when(members.findByTeamIdsSorted(List.of(10L)))
        .thenReturn(List.of(link(anne, alpha), link(paul, alpha)));
    when(reader.readAll(List.of(2L, 3L), WEEK))
        .thenReturn(Map.of(2L, List.of(hour(2L, WEEK, 90), hour(2L, WEEK.plusDays(2), 480))));

    TeamWeek week = service.getTeamWeekSummary(1L, 10L, WEEK);

    assertThat(week.weekEnd()).isEqualTo(WEEK.plusDays(6));
    assertThat(week.members()).hasSize(2);
    assertThat(week.members().get(0).totals().totalMinutes()).isEqualTo(570);
    assertThat(week.members().get(0).totals().minutesPerDay())
        .containsExactly(90, 0, 480, 0, 0, 0, 0);
    assertThat(week.members().get(1).totals().totalMinutes()).isZero();
    assertThat(week.members().get(1).totals().minutesPerDay()).containsExactly(0, 0, 0, 0, 0, 0, 0);
  }

  @Test
  @DisplayName(
      "CA-04-07 / RG-04-05 : une PLAGE du jeudi au mardi compte jeudi et vendredi, 14 h avec la journée figée")
  void rangeCountsOnlyWeekdaysOfTheDisplayedWeek() {
    User anne = account(2L, "Anne", "Adam", Role.USER, true);
    managerOfAlpha();
    when(members.findByTeamIdsSorted(List.of(10L))).thenReturn(List.of(link(anne, alpha)));
    when(reader.readAll(List.of(2L), WEEK))
        .thenReturn(
            Map.of(
                2L,
                List.of(range(2L, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 13), 480))));

    TeamWeek week = service.getTeamWeekSummary(1L, 10L, WEEK);

    assertThat(week.members().get(0).totals().totalMinutes()).isEqualTo(960);
    assertThat(week.members().get(0).totals().minutesPerDay())
        .containsExactly(0, 0, 0, 480, 480, 0, 0);
  }

  @Test
  @DisplayName(
      "RG-04-09 / RG-04-07 : l'ordre du dépôt est conservé et le membre inactif reste listé")
  void keepsRepositoryOrderAndInactiveMembers() {
    User anne = account(2L, "Anne", "Adam", Role.USER, false);
    User paul = account(3L, "Paul", "Petit", Role.USER, true);
    managerOfAlpha();
    when(members.findByTeamIdsSorted(List.of(10L)))
        .thenReturn(List.of(link(anne, alpha), link(paul, alpha)));
    when(reader.readAll(List.of(2L, 3L), WEEK)).thenReturn(Map.of());

    TeamWeek week = service.getTeamWeekSummary(1L, 10L, WEEK);

    assertThat(week.members()).extracting(m -> m.member().getId()).containsExactly(2L, 3L);
    assertThat(week.members().get(0).member().isActive()).isFalse();
  }

  @Test
  @DisplayName("RG-04-10 : seuls les membres de l'équipe sont lus, pas le manager")
  void onlyMembersAreRead() {
    managerOfAlpha();
    when(members.findByTeamIdsSorted(List.of(10L))).thenReturn(List.of());
    when(reader.readAll(List.of(), WEEK)).thenReturn(Map.of());

    assertThat(service.getTeamWeekSummary(1L, 10L, WEEK).members()).isEmpty();
    verify(reader).readAll(List.of(), WEEK);
  }
}
