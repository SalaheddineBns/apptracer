package com.apptracer.account.service;

/**
 * Port de révocation des sessions d'un compte, déclaré côté comptes et implémenté par {@code auth}
 * (les comptes ne dépendent pas du domaine d'authentification). Les appels se font dans la
 * transaction de la modification du compte.
 */
public interface SessionRevoker {

  /** RG-03-14 : révoque tous les refresh tokens actifs du compte désactivé. */
  void revokeAllOnDeactivation(Long userId);

  /** RG-03-14 : révoque tous les refresh tokens actifs du compte dont le rôle change. */
  void revokeAllOnRoleChange(Long userId);
}
