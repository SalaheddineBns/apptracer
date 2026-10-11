package com.apptracer.auth.domain;

/** Raison de révocation d'un refresh token (RG-03-07, RG-03-08, RG-03-14, RG-03-15). */
public enum RevocationReason {
  ROTATED,
  LOGOUT,
  ACCOUNT_DEACTIVATED,
  ROLE_CHANGED,
  PASSWORD_CHANGED
}
