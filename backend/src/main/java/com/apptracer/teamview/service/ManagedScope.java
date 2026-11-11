package com.apptracer.teamview.service;

import com.apptracer.common.error.ForbiddenOperationException;
import com.apptracer.team.domain.Team;
import com.apptracer.team.domain.TeamMember;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RG-04-02, RG-04-06: scope of a MANAGER, computed on each request from their identifier (the
 * token's): the non-deleted teams they manage and their current members.
 */
@Component
public class ManagedScope {

  static final String RG = "RG-04-02";
  static final String DENIED = "error.team-view.access-denied";

  private final TeamRepository teams;
  private final TeamMemberRepository members;

  public ManagedScope(TeamRepository teams, TeamMemberRepository members) {
    this.teams = teams;
    this.members = members;
  }

  /**
   * RG-04-02, RG-04-06: the account must, at this moment, belong to one of the manager's teams. The
   * manager is not a member of their own team (RG-04-10, RG-05-05). 403 whether the account exists
   * or not (H11).
   */
  @Transactional(readOnly = true)
  public TeamMember requireMember(Long managerId, Long userId) {
    return members
        .findManagedMember(userId, managerId)
        .orElseThrow(() -> new ForbiddenOperationException(RG, DENIED));
  }

  /** RG-04-02: 403 whether the team exists or not (H11). */
  @Transactional(readOnly = true)
  public Team requireTeam(Long managerId, Long teamId) {
    return teams
        .findActiveById(teamId)
        .filter(t -> t.getManager().getId().equals(managerId))
        .orElseThrow(() -> new ForbiddenOperationException(RG, DENIED));
  }

  /** RG-04-08: the manager's teams sorted by name (the first one is the default). */
  @Transactional(readOnly = true)
  public List<Team> teamsOf(Long managerId) {
    return teams.findActiveByManagerId(managerId);
  }
}
