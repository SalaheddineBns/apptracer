package com.apptracer.account.domain;

/** RG-03-10 : un compte porte exactement un rôle ; MANAGER inclut les droits USER. */
public enum Role {
  USER,
  MANAGER,
  ADMIN
}
