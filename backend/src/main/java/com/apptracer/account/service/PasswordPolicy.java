package com.apptracer.account.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Règle de mot de passe, partagée par le compte ADMIN initial, la création de compte et le
 * changement de mot de passe.
 */
public final class PasswordPolicy {

  public static final int MIN_LENGTH = 12;

  /** Limite technique de BCrypt. */
  public static final int MAX_BYTES = 72;

  private PasswordPolicy() {}

  /**
   * Liste des règles non respectées (clés de message, RG-08-06) ; vide si le mot de passe est
   * valide.
   */
  public static List<String> violations(String password) {
    List<String> violations = new ArrayList<>();
    if (password == null || password.isEmpty()) {
      violations.add("password.required");
      return violations;
    }
    // RG-03-02 : au moins 12 caractères, au moins une lettre et un chiffre
    if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
      violations.add("password.min-length");
    }
    if (password.codePoints().noneMatch(Character::isLetter)) {
      violations.add("password.letter");
    }
    if (password.codePoints().noneMatch(Character::isDigit)) {
      violations.add("password.digit");
    }
    // RG-03-03 : limite technique de BCrypt (72 octets)
    if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      violations.add("password.too-long");
    }
    return violations;
  }

  public static boolean isValid(String password) {
    return violations(password).isEmpty();
  }
}
