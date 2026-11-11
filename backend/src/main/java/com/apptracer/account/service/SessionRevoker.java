package com.apptracer.account.service;

/**
 * Port for revoking an account's sessions, declared on the accounts side and implemented by {@code
 * auth} (accounts do not depend on the authentication domain). Calls run within the transaction of
 * the account change.
 */
public interface SessionRevoker {

  /** RG-03-14: revokes all the active refresh tokens of the deactivated account. */
  void revokeAllOnDeactivation(Long userId);

  /** RG-03-14: revokes all the active refresh tokens of the account whose role changes. */
  void revokeAllOnRoleChange(Long userId);
}
