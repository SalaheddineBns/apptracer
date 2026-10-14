package com.apptracer.teamview.service;

import com.apptracer.team.domain.Team;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vue équipe en lecture seule (SFD-04). Aucune écriture (RG-04-03). */
@Service
public class TeamViewService {

  private final ManagedScope scope;

  public TeamViewService(ManagedScope scope) {
    this.scope = scope;
  }

  /** RG-04-01, RG-04-08 : équipes du manager du token, vide s'il n'en a aucune. */
  @Transactional(readOnly = true)
  public List<Team> listMyManagedTeams(Long managerId) {
    return scope.teamsOf(managerId);
  }
}
