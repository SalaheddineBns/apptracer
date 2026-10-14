package com.apptracer.teamview.service;

import com.apptracer.common.error.ForbiddenOperationException;
import com.apptracer.team.domain.Team;
import com.apptracer.team.repository.TeamRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RG-04-02, RG-04-06 : périmètre d'un MANAGER, calculé à chaque requête à partir de son identifiant
 * (celui du token) : équipes non supprimées dont il est manager et leurs membres actuels.
 */
@Component
public class ManagedScope {

  static final String RG = "RG-04-02";
  static final String DENIED = "Vous n'avez pas accès à cette équipe ou à ce compte.";

  private final TeamRepository teams;

  public ManagedScope(TeamRepository teams) {
    this.teams = teams;
  }

  /** RG-04-02 : 403 que l'équipe existe ou non (H11). */
  @Transactional(readOnly = true)
  public Team requireTeam(Long managerId, Long teamId) {
    return teams
        .findActiveById(teamId)
        .filter(t -> t.getManager().getId().equals(managerId))
        .orElseThrow(() -> new ForbiddenOperationException(RG, DENIED));
  }

  /** RG-04-08 : équipes du manager triées par nom (la première est celle par défaut). */
  @Transactional(readOnly = true)
  public List<Team> teamsOf(Long managerId) {
    return teams.findActiveByManagerId(managerId);
  }
}
