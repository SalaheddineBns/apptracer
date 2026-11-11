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

  /** Allowed sort properties (never the password hash). */
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
   * RG-03-11: ADMIN only (checked by the controller). The password's format and strength (RG-03-02)
   * are checked by Bean Validation on the DTO.
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
    // RG-03-01: unique e-mail, case-insensitive (the unique index covers concurrent creations; its
    // violation is translated into a 409 RG-03-01 by the global handler)
    if (users.existsByEmail(normalizedEmail)) {
      throw BusinessException.onField(
          "RG-03-01", "error.account.email-duplicate", "email", "field.account.email-duplicate");
    }
    // RG-03-12: initial password set by the ADMIN; RG-03-03: stored hashed with BCrypt. RG-03-10:
    // exactly one role; RG-03-05: active on creation
    User user =
        new User(
            normalizedEmail,
            firstName.strip(),
            lastName.strip(),
            passwordEncoder.encode(initialPassword),
            role);
    // RG-09-02, RG-09-04: optional job title, assigned by the ADMIN, never for an ADMIN
    user.setJobTitle(resolveJobTitle(role, jobTitleCode, null));
    // RG-00-04: created_by / updated_by filled in by JPA auditing (signed-in ADMIN)
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

  /** Account loaded with an exclusive lock ({@code FOR UPDATE}), for update and deactivate. */
  private User getForUpdate(Long id) {
    return users
        .findByIdForUpdate(id)
        .orElseThrow(() -> new ResourceNotFoundException("error.account.not-found"));
  }

  /**
   * RG-09-02, RG-09-06: job title to assign for the target role. {@code null} without a code.
   * Refused (409, field {@code jobTitleCode}) for an ADMIN, and for an unknown or inactive job
   * title unless the account already has it ({@code current}).
   */
  private JobTitle resolveJobTitle(Role role, String code, JobTitle current) {
    if (code == null) {
      return null;
    }
    // RG-09-02: an ADMIN account has no job title
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
    // RG-09-06: an inactive job title can no longer be assigned, but the one the account already
    // has may be kept
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

  /** RG-05-10: names of the managed teams, in quotes, separated by commas. */
  private static String quotedNames(List<String> teamNames) {
    return teamNames.stream().map(n -> "« " + n + " »").collect(Collectors.joining(", "));
  }

  /**
   * RG-03-11: only the first name, last name, role and job title (RG-09-04) can be changed (neither
   * e-mail nor password). Without {@code jobTitleCode}, the account no longer has a job title.
   * {@code actorId} is the signed-in ADMIN, read from the token.
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
    // Exclusive lock: serialises with team management (architecture §2.11)
    User user = getForUpdate(id);
    // Concurrent change: the version read by the ADMIN is no longer the version in the database
    if (user.getVersion() != version) {
      throw new ObjectOptimisticLockingFailureException(User.class, id);
    }
    // RG-03-13: an ADMIN cannot remove their own ADMIN role
    if (actorId.equals(id) && user.getRole() == Role.ADMIN && role != Role.ADMIN) {
      throw BusinessException.onField(
          "RG-03-13", "error.account.self-demote", "role", "field.account.self-demote");
    }
    // RG-05-10: the MANAGER role cannot be removed while the account manages a team
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
    user.setRole(role); // RG-03-10: exactly one role
    // RG-09-04: only the ADMIN assigns, changes or removes the job title; RG-09-03: changing the
    // role between USER and MANAGER leaves it untouched, and switching to ADMIN removes it (a code
    // received for an ADMIN is refused, RG-09-02); RG-09-11: recorded by the account's JPA auditing
    user.setJobTitle(resolveJobTitle(role, jobTitleCode, user.getJobTitle()));
    // RG-00-04: updated_at / updated_by filled in by JPA auditing
    User saved = users.saveAndFlush(user);
    if (role == Role.ADMIN && previousRole != Role.ADMIN) {
      // RG-05-12: an ADMIN can be a member of no team (RG-05-03); their link is removed in the same
      // transaction, and the team's change is recorded (RG-05-14)
      teamLinks.removeFromTeam(actorId, id);
    }
    if (roleChanged) {
      // RG-03-14: a role change revokes all the account's refresh tokens
      sessionRevoker.revokeAllOnRoleChange(id);
    }
    return saved;
  }

  /** No effect if the account is already inactive. */
  @Transactional
  public User deactivate(Long actorId, Long id) {
    // Exclusive lock: serialises with team management (architecture §2.11)
    User user = getForUpdate(id);
    // RG-03-13: an ADMIN cannot deactivate their own account
    if (actorId.equals(id)) {
      throw new BusinessException("RG-03-13", "error.account.self-deactivate");
    }
    if (!user.isActive()) {
      return user;
    }
    // RG-05-10: a team manager cannot be deactivated
    List<String> managed = teamLinks.managedTeamNames(id);
    if (!managed.isEmpty()) {
      throw new BusinessException(
          "RG-05-10",
          managed.size() == 1
              ? "error.account.manager-of-team.deactivate"
              : "error.account.manager-of-teams.deactivate",
          quotedNames(managed));
    }
    // RG-03-05: an inactive account can neither sign in nor renew its session
    user.setActive(false);
    User saved = users.saveAndFlush(user);
    // RG-03-14: deactivation revokes all the account's refresh tokens
    sessionRevoker.revokeAllOnDeactivation(id);
    return saved;
  }

  /** No effect if the account is already active. Revoked tokens are not restored. */
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
