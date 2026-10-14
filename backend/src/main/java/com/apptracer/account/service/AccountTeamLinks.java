package com.apptracer.account.service;

import java.util.List;

/**
 * Port vers les équipes, déclaré côté comptes et implémenté par {@code team} sur le modèle de
 * {@link SessionRevoker} (les comptes ne dépendent pas du domaine des équipes). Les appels se font
 * dans la transaction de la modification du compte.
 */
public interface AccountTeamLinks {

  /**
   * RG-05-10 : noms des équipes non supprimées dont le compte est manager, triés par nom. Liste
   * vide si le compte n'est manager d'aucune équipe.
   */
  List<String> managedTeamNames(Long userId);

  /**
   * RG-05-12 : retire le compte de son équipe, s'il en a une, et trace la modification de l'équipe
   * (RG-05-14). Sans effet si le compte n'est membre d'aucune équipe. {@code actorId} est l'ADMIN
   * connecté.
   */
  void removeFromTeam(Long actorId, Long userId);
}
