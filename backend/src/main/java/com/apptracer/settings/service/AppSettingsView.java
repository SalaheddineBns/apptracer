package com.apptracer.settings.service;

import java.time.Instant;

/**
 * Settings as read by the API. {@code lastModifiedAt} and {@code lastModifiedBy} are null as long
 * as the value has never been changed (RG-06-05), and for any role other than ADMIN (H7).
 */
public record AppSettingsView(
    int standardDayMinutes, long version, Instant lastModifiedAt, Author lastModifiedBy) {

  /** Author of the last change: only what is needed from the account. */
  public record Author(long id, String firstName, String lastName) {}
}
