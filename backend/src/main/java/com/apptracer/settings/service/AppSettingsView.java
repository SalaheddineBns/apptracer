package com.apptracer.settings.service;

import java.time.Instant;

/**
 * Lecture des paramètres pour l'API. {@code lastModifiedAt} et {@code lastModifiedBy} sont nuls
 * tant que la valeur n'a jamais été modifiée (RG-06-05), et pour tout autre rôle que l'ADMIN (H7).
 */
public record AppSettingsView(
    int standardDayMinutes, long version, Instant lastModifiedAt, Author lastModifiedBy) {

  /** Auteur de la dernière modification : le strict nécessaire sur le compte. */
  public record Author(long id, String firstName, String lastName) {}
}
