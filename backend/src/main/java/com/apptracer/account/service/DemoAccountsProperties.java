package com.apptracer.account.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propriétés {@code app.demo.*} (DEMO_PASSWORD) des comptes de démonstration. */
@ConfigurationProperties(prefix = "app.demo")
public record DemoAccountsProperties(String password) {

  /** Le mot de passe ne doit jamais apparaître dans un log. */
  @Override
  public String toString() {
    return "DemoAccountsProperties[password=***]";
  }
}
