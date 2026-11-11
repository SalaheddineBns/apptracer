package com.apptracer.account.service;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * RG-03-16: creates the initial ADMIN account at start-up (after Flyway), from the configuration.
 * Idempotent: nothing is done if an ADMIN account already exists, and no existing account is ever
 * changed. The password appears in no message or log.
 */
@Component
@Order(0)
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class InitialAdminInitializer implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(InitialAdminInitializer.class);

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final BootstrapAdminProperties properties;
  private final Validator validator;

  public InitialAdminInitializer(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      BootstrapAdminProperties properties,
      Validator validator) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
    this.validator = validator;
  }

  @Override
  public void run(ApplicationArguments args) {
    createIfAbsent();
  }

  /** Returns true if the ADMIN account was created. */
  public boolean createIfAbsent() {
    // RG-03-16 : création seulement si aucun compte ADMIN n'existe
    if (users.existsByRole(Role.ADMIN)) {
      return false;
    }
    String email = trim(properties.email());
    String firstName = trim(properties.firstName());
    String lastName = trim(properties.lastName());
    validate(email, properties.password(), firstName, lastName);

    // RG-03-01 : l'e-mail est unique ; un compte existant n'est jamais modifié
    if (users.existsByEmail(email)) {
      throw new IllegalStateException(
          "Configuration du compte ADMIN initial invalide : ADMIN_EMAIL correspond à un compte "
              + "existant qui n'est pas ADMIN.");
    }
    // RG-03-03 : mot de passe haché en BCrypt ; created_by / updated_by restent NULL (système)
    User admin =
        new User(
            email, firstName, lastName, passwordEncoder.encode(properties.password()), Role.ADMIN);
    users.save(admin);
    LOG.info("Compte ADMIN initial créé (id={}).", admin.getId());
    return true;
  }

  private record EmailHolder(@Email String value) {}

  private void validate(String email, String password, String firstName, String lastName) {
    List<String> problems = new ArrayList<>();
    // RG-03-01 : format e-mail valide, 254 caractères au plus
    if (email.isEmpty()) {
      problems.add("ADMIN_EMAIL est obligatoire");
    } else if (email.length() > 254 || !validator.validate(new EmailHolder(email)).isEmpty()) {
      problems.add("ADMIN_EMAIL n'est pas une adresse e-mail valide");
    }
    // RG-03-02 : règle de mot de passe (seuls les motifs sont affichés, jamais la valeur)
    List<String> passwordViolations = PasswordPolicy.violations(password);
    if (!passwordViolations.isEmpty()) {
      problems.add("ADMIN_PASSWORD invalide (" + String.join(" ", passwordViolations) + ")");
    }
    if (firstName.isEmpty() || firstName.length() > 100) {
      problems.add("ADMIN_FIRST_NAME est obligatoire (100 caractères au plus)");
    }
    if (lastName.isEmpty() || lastName.length() > 100) {
      problems.add("ADMIN_LAST_NAME est obligatoire (100 caractères au plus)");
    }
    if (!problems.isEmpty()) {
      throw new IllegalStateException(
          "Aucun compte ADMIN n'existe et la configuration du compte ADMIN initial est invalide : "
              + String.join(" ; ", problems)
              + ".");
    }
  }

  private static String trim(String value) {
    return value == null ? "" : value.strip();
  }
}
