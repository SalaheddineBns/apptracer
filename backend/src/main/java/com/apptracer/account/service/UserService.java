package com.apptracer.account.service;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.InvalidRequestException;
import com.apptracer.common.error.ResourceNotFoundException;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  /** Propriétés de tri autorisées (jamais le haché du mot de passe). */
  private static final Set<String> SORTABLE =
      Set.of("lastName", "firstName", "email", "role", "active");

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * RG-03-11 : réservé à l'ADMIN (contrôlé par le contrôleur). Le format et la force du mot de
   * passe (RG-03-02) sont contrôlés par Bean Validation sur le DTO.
   */
  @Transactional
  public User create(
      String email, String firstName, String lastName, Role role, String initialPassword) {
    String normalizedEmail = email.strip();
    // RG-03-01 : e-mail unique, insensible à la casse (l'index unique couvre les créations
    // concurrentes ; sa violation est traduite en 409 RG-03-01 par le handler global)
    if (users.existsByEmail(normalizedEmail)) {
      throw BusinessException.onField(
          "RG-03-01",
          "Un compte existe déjà avec cet e-mail.",
          "email",
          "Cet e-mail est déjà utilisé.");
    }
    // RG-03-12 : mot de passe initial défini par l'ADMIN ; RG-03-03 : stocké haché en BCrypt
    // RG-03-10 : exactement un rôle ; RG-03-05 : actif à la création
    User user =
        new User(
            normalizedEmail,
            firstName.strip(),
            lastName.strip(),
            passwordEncoder.encode(initialPassword),
            role);
    // RG-00-04 : created_by / updated_by renseignés par l'audit JPA (ADMIN connecté)
    return users.save(user);
  }

  @Transactional(readOnly = true)
  public Page<User> list(Pageable pageable) {
    for (Sort.Order order : pageable.getSort()) {
      if (!SORTABLE.contains(order.getProperty())) {
        throw new InvalidRequestException(
            "Paramètre de tri invalide.", "sort", "Tri non autorisé : " + order.getProperty());
      }
    }
    return users.findAll(
        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort()));
  }

  @Transactional(readOnly = true)
  public User get(Long id) {
    return users
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Compte introuvable."));
  }
}
