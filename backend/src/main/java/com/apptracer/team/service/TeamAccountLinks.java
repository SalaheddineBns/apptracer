package com.apptracer.team.service;

import com.apptracer.account.service.AccountTeamLinks;
import com.apptracer.team.domain.Team;
import com.apptracer.team.domain.TeamMember;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** Implémentation, côté équipes, du port {@link AccountTeamLinks} utilisé par les comptes. */
@Service
public class TeamAccountLinks implements AccountTeamLinks {

  private final TeamRepository teams;
  private final TeamMemberRepository members;
  private final Clock clock;

  public TeamAccountLinks(TeamRepository teams, TeamMemberRepository members, Clock clock) {
    this.teams = teams;
    this.members = members;
    this.clock = clock;
  }

  @Override
  public List<String> managedTeamNames(Long userId) {
    return teams.findActiveNamesByManagerId(userId);
  }

  @Override
  public void removeFromTeam(Long actorId, Long userId) {
    // RG-05-04 : au plus un rattachement par compte
    for (TeamMember link : members.findWithTeamByUserIds(List.of(userId))) {
      Team team = link.getTeam();
      members.delete(link);
      // RG-05-14 : le retrait est une modification de l'équipe (date, auteur, version)
      team.touch(Instant.now(clock), actorId);
      teams.saveAndFlush(team);
    }
  }
}
