package com.apptracer.account.service;

import java.util.List;

/**
 * Port to the teams, declared on the accounts side and implemented by {@code team}, on the model of
 * {@link SessionRevoker} (accounts do not depend on the teams domain). Calls run within the
 * transaction of the account change.
 */
public interface AccountTeamLinks {

  /**
   * RG-05-10: names of the non-deleted teams the account manages, sorted by name. Empty list if the
   * account manages no team.
   */
  List<String> managedTeamNames(Long userId);

  /**
   * RG-05-12: removes the account from its team, if it has one, and records the team's change
   * (RG-05-14). No effect if the account belongs to no team. {@code actorId} is the signed-in
   * ADMIN.
   */
  void removeFromTeam(Long actorId, Long userId);
}
