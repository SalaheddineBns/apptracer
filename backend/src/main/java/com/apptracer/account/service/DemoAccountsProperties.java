package com.apptracer.account.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Properties {@code app.demo.*} (DEMO_PASSWORD) of the demo accounts. */
@ConfigurationProperties(prefix = "app.demo")
public record DemoAccountsProperties(String password) {

  /** The password must never appear in a log. */
  @Override
  public String toString() {
    return "DemoAccountsProperties[password=***]";
  }
}
