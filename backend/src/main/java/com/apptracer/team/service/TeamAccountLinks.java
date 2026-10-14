package com.apptracer.team.service;

import com.apptracer.account.service.AccountTeamLinks;
import com.apptracer.team.repository.TeamRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** Implémentation, côté équipes, du port {@link AccountTeamLinks} utilisé par les comptes. */
@Service
public class TeamAccountLinks implements AccountTeamLinks {

  private final TeamRepository teams;

  public TeamAccountLinks(TeamRepository teams) {
    this.teams = teams;
  }

  @Override
  public List<String> managedTeamNames(Long userId) {
    return teams.findActiveNamesByManagerId(userId);
  }
}
