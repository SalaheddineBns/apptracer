package com.apptracer.teamview.service;

import com.apptracer.account.domain.User;
import com.apptracer.common.error.InvalidRequestException;
import com.apptracer.team.domain.Team;
import com.apptracer.team.domain.TeamMember;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.timeentry.domain.TimeEntry;
import com.apptracer.timeentry.service.WeeklyEntriesReader;
import com.apptracer.timeentry.service.WeeklyTimeEntries;
import com.apptracer.timeentry.service.WeeklyTotalCalculator;
import com.apptracer.timeentry.service.WeeklyTotalCalculator.WeekTotals;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only team view (SFD-04). No writes (RG-04-03). */
@Service
public class TeamViewService {

  private final ManagedScope scope;
  private final TeamMemberRepository teamMembers;
  private final WeeklyEntriesReader reader;
  private final WeeklyTotalCalculator calculator = new WeeklyTotalCalculator();

  public TeamViewService(
      ManagedScope scope, TeamMemberRepository teamMembers, WeeklyEntriesReader reader) {
    this.scope = scope;
    this.teamMembers = teamMembers;
    this.reader = reader;
  }

  public record MemberWeek(User member, WeekTotals totals) {}

  public record TeamWeek(
      Team team, LocalDate weekStart, LocalDate weekEnd, List<MemberWeek> members) {}

  public record MemberJournal(Team team, User member, WeeklyTimeEntries week) {}

  /** RG-04-01, RG-04-08: teams of the token's manager, empty if they manage none. */
  @Transactional(readOnly = true)
  public List<Team> listMyManagedTeams(Long managerId) {
    return scope.teamsOf(managerId);
  }

  @Transactional(readOnly = true)
  public TeamWeek getTeamWeekSummary(Long managerId, Long teamId, LocalDate weekStart) {
    // RG-04-02: membership check before anything else
    Team team = scope.requireTeam(managerId, teamId);
    requireMonday(weekStart);
    // RG-04-09, RG-04-10: members sorted by last name then first name; the manager is not a member
    List<TeamMember> links = teamMembers.findByTeamIdsSorted(List.of(team.getId()));
    // RG-04-05, RG-01-11: non-deleted time entries of the week, in one query
    Map<Long, List<TimeEntry>> entries =
        reader.readAll(links.stream().map(TeamMember::getUserId).toList(), weekStart);
    List<MemberWeek> members =
        links.stream()
            .map(
                l ->
                    new MemberWeek(
                        // RG-04-07: the inactive account stays listed, flagged by active
                        l.getUser(),
                        // RG-04-05: same computations as the member's journal
                        calculator.compute(
                            entries.getOrDefault(l.getUserId(), List.of()), weekStart)))
            .toList();
    return new TeamWeek(team, weekStart, weekStart.plusDays(6), members);
  }

  @Transactional(readOnly = true)
  public MemberJournal listTeamMemberTimeEntries(Long managerId, Long userId, LocalDate weekStart) {
    // RG-04-02, RG-04-06 : appartenance actuelle, 403 sinon
    TeamMember link = scope.requireMember(managerId, userId);
    requireMonday(weekStart);
    // RG-04-05, RG-04-07: same read as the member's journal, inactive account included
    List<TimeEntry> entries = reader.read(userId, weekStart);
    var totals = calculator.compute(entries, weekStart);
    return new MemberJournal(
        link.getTeam(),
        link.getUser(),
        new WeeklyTimeEntries(
            weekStart,
            weekStart.plusDays(6),
            totals.totalMinutes(),
            totals.minutesPerDay(),
            entries));
  }

  // RG-04-04: the week runs from Monday to Sunday
  private static void requireMonday(LocalDate weekStart) {
    if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
      throw new InvalidRequestException("error.week.not-monday", "weekStart", "field.not-monday");
    }
  }
}
