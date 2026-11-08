package com.apptracer.account.service;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.InvalidRequestException;
import com.apptracer.common.error.ResourceNotFoundException;
import com.apptracer.referential.domain.JobTitle;
import com.apptracer.referential.repository.JobTitleRepository;
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
  private final JobTitleRepository jobTitles;

  public UserService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      SessionRevoker sessionRevoker,
      AccountTeamLinks teamLinks,
      JobTitleRepository jobTitles) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.sessionRevoker = sessionRevoker;
    this.teamLinks = teamLinks;
    this.jobTitles = jobTitles;
  }

  /**
   * RG-03-11 : réservé à l'ADMIN (contrôlé par le contrôleur). Le format et la force du mot de
   * passe (RG-03-02) sont contrôlés par Bean Validation sur le DTO.
   */
  @Transactional
  public User create(
      String email,
      String firstName,
      String lastName,
      Role role,
      String initialPassword,
      String jobTitleCode) {
    String normalizedEmail = email.strip();
    // RG-03-01 : e-mail unique, insensible à la casse (l'index unique couvre les créations
    // concurrentes ; sa violation est traduite en 409 RG-03-01 par le handler global)
    if (users.existsByEmail(normalizedEmail)) {
      throw BusinessException.onField(
          "RG-03-01", "error.account.email-duplicate", "email", "field.account.email-duplicate");
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
    // RG-09-02, RG-09-04 : fonction facultative, attribuée par l'ADMIN, jamais pour un ADMIN
    user.setJobTitle(resolveJobTitle(role, jobTitleCode, null));
    // RG-00-04 : created_by / updated_by renseignés par l'audit JPA (ADMIN connecté)
    return users.save(user);
  }

  @Transactional(readOnly = true)
  public Page<User> list(Pageable pageable) {
    for (Sort.Order order : pageable.getSort()) {
      if (!SORTABLE.contains(order.getProperty())) {
        throw new InvalidRequestException(
            "error.sort.invalid", "sort", "field.sort.invalid", order.getProperty());
      }
    }
    return users.findAll(
        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort()));
  }

  @Transactional(readOnly = true)
  public User get(Long id) {
    return users
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("error.account.not-found"));
  }

  /** Compte chargé avec un verrou exclusif ({@code FOR UPDATE}), pour update et deactivate. */
  private User getForUpdate(Long id) {
    return users
        .findByIdForUpdate(id)
        .orElseThrow(() -> new ResourceNotFoundException("error.account.not-found"));
  }

  /**
   * RG-09-02, RG-09-06 : fonction à attribuer pour le rôle visé. {@code null} sans code. Refus
   * (409, champ {@code jobTitleCode}) pour un ADMIN, et pour une fonction inconnue ou inactive sauf
   * si le compte la porte déjà ({@code current}).
   */
  private JobTitle resolveJobTitle(Role role, String code, JobTitle current) {
    if (code == null) {
      return null;
    }
    // RG-09-02 : un compte ADMIN n'a pas de fonction
    if (role == Role.ADMIN) {
      throw BusinessException.onField(
          "RG-09-02",
          "error.account.job-title-admin",
          "jobTitleCode",
          "field.account.job-title-admin");
    }
    JobTitle title =
        jobTitles
            .findByCode(code)
            .orElseThrow(
                () ->
                    BusinessException.onField(
                        "RG-09-06",
                        "error.job-title.unknown-or-inactive",
                        "jobTitleCode",
                        "field.job-title.unknown-or-inactive"));
    // RG-09-06 : une fonction inactive n'est plus attribuable, mais celle que le compte porte déjà
    // peut être laissée
    boolean alreadyCarried = current != null && current.getId().equals(title.getId());
    if (!title.isActive() && !alreadyCarried) {
      throw BusinessException.onField(
          "RG-09-06",
          "error.job-title.unknown-or-inactive",
          "jobTitleCode",
          "field.job-title.unknown-or-inactive");
    }
    return title;
  }

  /** RG-05-10 : noms des équipes gérées, entre guillemets, séparés par des virgules. */
  private static String quotedNames(List<String> teamNames) {
    return teamNames.stream().map(n -> "« " + n + " »").collect(Collectors.joining(", "));
  }

  /**
   * RG-03-11 : seuls le prénom, le nom, le rôle et la fonction (RG-09-04) sont modifiables (ni
   * e-mail, ni mot de passe). Sans {@code jobTitleCode}, le compte n'a plus de fonction. {@code
   * actorId} est l'ADMIN connecté, lu dans le token.
   */
  @Transactional
  public User update(
      Long actorId,
      Long id,
      String firstName,
      String lastName,
      Role role,
      String jobTitleCode,
      long version) {
    // Verrou exclusif : sérialise avec la gestion des équipes (architecture §2.11)
    User user = getForUpdate(id);
    // Modification concurrente : la version lue par l'ADMIN n'est plus la version en base
    if (user.getVersion() != version) {
      throw new ObjectOptimisticLockingFailureException(User.class, id);
    }
    // RG-03-13 : un ADMIN ne peut pas retirer son propre rôle ADMIN
    if (actorId.equals(id) && user.getRole() == Role.ADMIN && role != Role.ADMIN) {
      throw BusinessException.onField(
          "RG-03-13", "error.account.self-demote", "role", "field.account.self-demote");
    }
    // RG-05-10 : le rôle MANAGER ne se retire pas tant que le compte manage une équipe
    if (user.getRole() == Role.MANAGER && role != Role.MANAGER) {
      List<String> managed = teamLinks.managedTeamNames(id);
      if (!managed.isEmpty()) {
        throw BusinessException.onField(
            "RG-05-10",
            managed.size() == 1
                ? "error.account.manager-of-team.demote"
                : "error.account.manager-of-teams.demote",
            "role",
            "field.account.manager-of-team.demote",
            quotedNames(managed));
      }
    }
    Role previousRole = user.getRole();
    boolean roleChanged = previousRole != role;
    user.setFirstName(firstName.strip());
    user.setLastName(lastName.strip());
    user.setRole(role); // RG-03-10 : exactement un rôle
    // RG-09-04 : seul l'ADMIN attribue, change ou retire la fonction ; RG-09-03 : changer le rôle
    // entre USER et MANAGER ne la touche pas, et le passage à ADMIN la retire (un code reçu pour un
    // ADMIN est refusé, RG-09-02) ; RG-09-11 : tracé par l'audit JPA du compte
    user.setJobTitle(resolveJobTitle(role, jobTitleCode, user.getJobTitle()));
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
      throw new BusinessException("RG-03-13", "error.account.self-deactivate");
    }
    if (!user.isActive()) {
      return user;
    }
    // RG-05-10 : un manager d'équipe ne se désactive pas
    List<String> managed = teamLinks.managedTeamNames(id);
    if (!managed.isEmpty()) {
      throw new BusinessException(
          "RG-05-10",
          managed.size() == 1
              ? "error.account.manager-of-team.deactivate"
              : "error.account.manager-of-teams.deactivate",
          quotedNames(managed));
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
