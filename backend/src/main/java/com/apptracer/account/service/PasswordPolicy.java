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
   * Liste des règles non respectées (messages en français) ; vide si le mot de passe est valide.
   */
  public static List<String> violations(String password) {
    List<String> violations = new ArrayList<>();
    if (password == null || password.isEmpty()) {
      violations.add("Le mot de passe est obligatoire.");
      return violations;
    }
    // RG-03-02 : au moins 12 caractères, au moins une lettre et un chiffre
    if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
      violations.add("Le mot de passe doit faire au moins " + MIN_LENGTH + " caractères.");
    }
    if (password.codePoints().noneMatch(Character::isLetter)) {
      violations.add("Le mot de passe doit contenir au moins une lettre.");
    }
    if (password.codePoints().noneMatch(Character::isDigit)) {
      violations.add("Le mot de passe doit contenir au moins un chiffre.");
    }
    // RG-03-03 : limite technique de BCrypt (72 octets)
    if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      violations.add("Le mot de passe est trop long (" + MAX_BYTES + " octets au maximum).");
    }
    return violations;
  }

  public static boolean isValid(String password) {
    return violations(password).isEmpty();
  }
}
