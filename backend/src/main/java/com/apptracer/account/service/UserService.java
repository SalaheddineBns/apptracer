package com.apptracer.account.service;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.InvalidRequestException;
import com.apptracer.common.error.ResourceNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
  private final SessionRevoker sessionRevoker;
  private final AccountTeamLinks teamLinks;

  public UserService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      SessionRevoker sessionRevoker,
      AccountTeamLinks teamLinks) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.sessionRevoker = sessionRevoker;
    this.teamLinks = teamLinks;
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

  /** Compte chargé avec un verrou exclusif ({@code FOR UPDATE}), pour update et deactivate. */
  private User getForUpdate(Long id) {
    return users
        .findByIdForUpdate(id)
        .orElseThrow(() -> new ResourceNotFoundException("Compte introuvable."));
  }

  /** RG-05-10 : message nommant la ou les équipes dont le compte est manager. */
  private static String managerOfMessage(List<String> teamNames, String action) {
    String names = teamNames.stream().map(n -> "« " + n + " »").collect(Collectors.joining(", "));
    return teamNames.size() == 1
        ? "Ce compte est manager de l'équipe %s. Désignez un autre manager ou supprimez l'équipe avant de %s."
            .formatted(names, action)
        : "Ce compte est manager des équipes %s. Désignez un autre manager ou supprimez ces équipes avant de %s."
            .formatted(names, action);
  }

  /**
   * RG-03-11 : seuls le prénom, le nom et le rôle sont modifiables (ni e-mail, ni mot de passe).
   * {@code actorId} est l'ADMIN connecté, lu dans le token.
   */
  @Transactional
  public User update(
      Long actorId, Long id, String firstName, String lastName, Role role, long version) {
    // Verrou exclusif : sérialise avec la gestion des équipes (architecture §2.11)
    User user = getForUpdate(id);
    // Modification concurrente : la version lue par l'ADMIN n'est plus la version en base
    if (user.getVersion() != version) {
      throw new ObjectOptimisticLockingFailureException(User.class, id);
    }
    // RG-03-13 : un ADMIN ne peut pas retirer son propre rôle ADMIN
    if (actorId.equals(id) && user.getRole() == Role.ADMIN && role != Role.ADMIN) {
      throw BusinessException.onField(
          "RG-03-13",
          "Vous ne pouvez pas retirer votre propre rôle ADMIN.",
          "role",
          "Votre propre rôle ADMIN ne peut pas être retiré.");
    }
    // RG-05-10 : le rôle MANAGER ne se retire pas tant que le compte manage une équipe
    if (user.getRole() == Role.MANAGER && role != Role.MANAGER) {
      List<String> managed = teamLinks.managedTeamNames(id);
      if (!managed.isEmpty()) {
        throw BusinessException.onField(
            "RG-05-10",
            managerOfMessage(managed, "lui retirer le rôle MANAGER"),
            "role",
            "Le rôle MANAGER ne peut pas être retiré : le compte est manager d'une équipe.");
      }
    }
    Role previousRole = user.getRole();
    boolean roleChanged = previousRole != role;
    user.setFirstName(firstName.strip());
    user.setLastName(lastName.strip());
    user.setRole(role); // RG-03-10 : exactement un rôle
    // RG-00-04 : updated_at / updated_by renseignés par l'audit JPA
    User saved = users.saveAndFlush(user);
    if (role == Role.ADMIN && previousRole != Role.ADMIN) {
      // RG-05-12 : un ADMIN ne peut être membre d'aucune équipe (RG-05-03), son rattachement est
      // retiré dans la même transaction ; la modification de l'équipe est tracée (RG-05-14)
      teamLinks.removeFromTeam(actorId, id);
    }
    if (roleChanged) {
      // RG-03-14 : un changement de rôle révoque tous les refresh tokens du compte
      sessionRevoker.revokeAllOnRoleChange(id);
    }
    return saved;
  }

  /** Sans effet si le compte est déjà inactif. */
  @Transactional
  public User deactivate(Long actorId, Long id) {
    // Verrou exclusif : sérialise avec la gestion des équipes (architecture §2.11)
    User user = getForUpdate(id);
    // RG-03-13 : un ADMIN ne peut pas désactiver son propre compte
    if (actorId.equals(id)) {
      throw new BusinessException("RG-03-13", "Vous ne pouvez pas désactiver votre propre compte.");
    }
    if (!user.isActive()) {
      return user;
    }
    // RG-05-10 : un manager d'équipe ne se désactive pas
    List<String> managed = teamLinks.managedTeamNames(id);
    if (!managed.isEmpty()) {
      throw new BusinessException("RG-05-10", managerOfMessage(managed, "le désactiver"));
    }
    // RG-03-05 : un compte inactif ne peut ni se connecter ni renouveler sa session
    user.setActive(false);
    User saved = users.saveAndFlush(user);
    // RG-03-14 : la désactivation révoque tous les refresh tokens du compte
    sessionRevoker.revokeAllOnDeactivation(id);
    return saved;
  }

  /** Sans effet si le compte est déjà actif. Les jetons révoqués ne sont pas restaurés. */
  @Transactional
  public User reactivate(Long id) {
    User user = get(id);
    if (user.isActive()) {
      return user;
    }
    user.setActive(true);
    return users.saveAndFlush(user);
  }
}
