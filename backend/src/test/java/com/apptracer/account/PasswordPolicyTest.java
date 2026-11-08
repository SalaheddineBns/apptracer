package com.apptracer.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.apptracer.account.api.ValidPassword;
import com.apptracer.account.service.PasswordPolicy;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

  record Form(@ValidPassword String password) {}

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("RG-03-02 : 12 caractères avec une lettre et un chiffre sont acceptés")
  void validPassword() {
    assertThat(PasswordPolicy.isValid("abcdefghijk1")).isTrue();
  }

  @Test
  @DisplayName("RG-03-02 : moins de 12 caractères refusé")
  void tooShort() {
    assertThat(PasswordPolicy.violations("abcdefghij1")).hasSize(1);
    assertThat(PasswordPolicy.isValid("Abcd1234")).isFalse();
  }

  @Test
  @DisplayName("RG-03-02 : sans chiffre ou sans lettre refusé")
  void needsLetterAndDigit() {
    assertThat(PasswordPolicy.isValid("abcdefghijklm")).isFalse();
    assertThat(PasswordPolicy.isValid("1234567890123")).isFalse();
  }

  @Test
  @DisplayName(
      "RG-03-03 : plus de 72 octets refusé (limite BCrypt), en octets et non en caractères")
  void tooLongInBytes() {
    assertThat(PasswordPolicy.isValid("a".repeat(71) + "1")).isTrue();
    assertThat(PasswordPolicy.isValid("a".repeat(72) + "1")).isFalse();
    assertThat(PasswordPolicy.isValid("é".repeat(40) + "1")).isFalse();
  }

  @Test
  @DisplayName("RG-03-02 : null ou vide refusé par la politique")
  void nullOrEmpty() {
    assertThat(PasswordPolicy.isValid(null)).isFalse();
    assertThat(PasswordPolicy.isValid("")).isFalse();
  }

  @Test
  @DisplayName("RG-03-02 : la contrainte @ValidPassword s'appuie sur la même règle")
  void constraintAnnotation() {
    assertThat(validator.validate(new Form("abcdefghijk1"))).isEmpty();
    var violations = validator.validate(new Form("court1"));
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getMessage()).contains("password.min-length");
  }
}
