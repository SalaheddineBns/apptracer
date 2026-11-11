package com.apptracer.team.service;

import com.apptracer.team.domain.Team;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RG-10-03: teams whose projects an account may use in time entries: the team the account belongs
 * to (RG-05-04) and, for a MANAGER, the teams they manage (RG-05-02, RG-05-06).
 */
@Component
public class TeamProjectScope {

  private final TeamMemberRepository members;
  private final TeamRepository teams;

  public TeamProjectScope(TeamMemberRepository members, TeamRepository teams) {
    this.members = members;
    this.teams = teams;
  }

  @Transactional(readOnly = true)
  public Set<Long> teamIdsOf(Long userId) {
    Set<Long> ids = new HashSet<>();
    members.findByUserId(userId).ifPresent(member -> ids.add(member.getTeamId()));
    teams.findActiveByManagerId(userId).stream().map(Team::getId).forEach(ids::add);
    return ids;
  }
}
