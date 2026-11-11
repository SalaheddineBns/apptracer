package com.apptracer.team.service;

import com.apptracer.team.domain.Team;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RG-10-03 : équipes dont les projets sont autorisés à la saisie pour un compte : l'équipe dont il
 * est membre (RG-05-04) et, pour un MANAGER, les équipes qu'il dirige (RG-05-02, RG-05-06).
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
