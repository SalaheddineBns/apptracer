package com.apptracer.teamview.api;

import com.apptracer.account.domain.User;
import com.apptracer.team.domain.Team;
import com.apptracer.teamview.service.TeamViewService.MemberJournal;
import com.apptracer.teamview.service.TeamViewService.MemberWeek;
import com.apptracer.teamview.service.TeamViewService.TeamWeek;
import com.apptracer.timeentry.api.TimeEntryMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TeamViewMapper {

  private final TimeEntryMapper timeEntryMapper;

  public TeamViewMapper(TimeEntryMapper timeEntryMapper) {
    this.timeEntryMapper = timeEntryMapper;
  }

  public ManagedTeamResponse toResponse(Team t) {
    return new ManagedTeamResponse(t.getId(), t.getName());
  }

  private TeamViewMember toMember(User u) {
    return new TeamViewMember(u.getId(), u.getFirstName(), u.getLastName(), u.isActive());
  }

  public TeamWeekSummaryResponse toResponse(TeamWeek w) {
    return new TeamWeekSummaryResponse(
        toResponse(w.team()),
        w.weekStart(),
        w.weekEnd(),
        w.members().stream().map(m -> toSummary(m, w)).toList());
  }

  private MemberWeekSummary toSummary(MemberWeek m, TeamWeek w) {
    List<DailyTotal> days = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      days.add(new DailyTotal(w.weekStart().plusDays(i), m.totals().minutesPerDay().get(i)));
    }
    return new MemberWeekSummary(toMember(m.member()), days, m.totals().totalMinutes());
  }

  public MemberWeeklyTimeEntriesResponse toResponse(MemberJournal j) {
    return new MemberWeeklyTimeEntriesResponse(
        toResponse(j.team()), toMember(j.member()), timeEntryMapper.toResponse(j.week()));
  }
}
