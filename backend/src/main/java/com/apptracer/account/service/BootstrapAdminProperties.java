package com.apptracer.account.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Properties {@code app.bootstrap.admin.*} (ADMIN_EMAIL, ADMIN_PASSWORD, ...), RG-03-16. */
@ConfigurationProperties(prefix = "app.bootstrap.admin")
public record BootstrapAdminProperties(
    String email, String password, String firstName, String lastName) {

  /** The password must never appear in a log. */
  @Override
  public String toString() {
    return "BootstrapAdminProperties[password=***]";
  }
}
