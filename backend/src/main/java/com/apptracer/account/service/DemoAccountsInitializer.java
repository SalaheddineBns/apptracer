package com.apptracer.account.service;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.referential.repository.JobTitleRepository;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import com.apptracer.team.service.TeamService;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Development tool (outside the specs): at start-up, creates a demo manager, developer and QA, then
 * the team Demo team that brings them together, only if DEMO_PASSWORD is set (never in production).
 * Idempotent: an e-mail that already exists is skipped, no existing account is changed, and the
 * team is created only if the manager leads no team and neither member has one. The ADMIN comes
 * from {@link InitialAdminInitializer}, which runs first. The password appears in no message or
 * log.
 */
@Component
@Order(1)
@EnableConfigurationProperties(DemoAccountsProperties.class)
public class DemoAccountsInitializer implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(DemoAccountsInitializer.class);

  /** Demo account; {@code jobTitleCode} is {@code null} for no job title. */
  public record DemoAccount(
      String email, String firstName, String lastName, Role role, String jobTitleCode) {}

  public static final DemoAccount MANAGER =
      new DemoAccount("manager@apptracer.local", "Marion", "Leroy", Role.MANAGER, null);
  public static final List<DemoAccount> MEMBERS =
      List.of(
          new DemoAccount("dev@apptracer.local", "Damien", "Petit", Role.USER, "DEVELOPER"),
          new DemoAccount("qa@apptracer.local", "Quentin", "Moreau", Role.USER, "QA"));
  public static final List<DemoAccount> ACCOUNTS = List.of(MANAGER, MEMBERS.get(0), MEMBERS.get(1));
  public static final String TEAM_NAME = "Demo team";

  private final UserRepository users;
  private final JobTitleRepository jobTitles;
  private final PasswordEncoder passwordEncoder;
  private final DemoAccountsProperties properties;
  private final TeamService teamService;
  private final TeamRepository teams;
  private final TeamMemberRepository teamMembers;

  public DemoAccountsInitializer(
      UserRepository users,
      JobTitleRepository jobTitles,
      PasswordEncoder passwordEncoder,
      DemoAccountsProperties properties,
      TeamService teamService,
      TeamRepository teams,
      TeamMemberRepository teamMembers) {
    this.users = users;
    this.jobTitles = jobTitles;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
    this.teamService = teamService;
    this.teams = teams;
    this.teamMembers = teamMembers;
  }

  @Override
  public void run(ApplicationArguments args) {
    createMissing();
    createTeamIfAbsent();
  }

  private boolean enabled() {
    String password = properties.password();
    return password != null && !password.isBlank();
  }

  /** Returns the number of accounts created. */
  public int createMissing() {
    if (!enabled()) {
      return 0;
    }
    // RG-03-02 : même règle de mot de passe que pour tout compte (seuls les motifs sont affichés)
    List<String> violations = PasswordPolicy.violations(properties.password());
    if (!violations.isEmpty()) {
      LOG.warn(
          "Comptes de démonstration non créés : DEMO_PASSWORD invalide ({}).",
          String.join(" ", violations));
      return 0;
    }
    String hash = passwordEncoder.encode(properties.password());
    int created = 0;
    for (DemoAccount account : ACCOUNTS) {
      if (users.existsByEmail(account.email())) {
        continue;
      }
      User user =
          new User(account.email(), account.firstName(), account.lastName(), hash, account.role());
      if (account.jobTitleCode() != null) {
        jobTitles.findByCode(account.jobTitleCode()).ifPresent(user::setJobTitle);
      }
      users.save(user);
      created++;
    }
    if (created > 0) {
      LOG.info("{} compte(s) de démonstration créé(s).", created);
    }
    return created;
  }

  /**
   * Creates Demo team (demo manager, with the developer and the QA as members) following the SFD-05
   * rules, on behalf of the first active ADMIN. Returns true if the team was created.
   */
  public boolean createTeamIfAbsent() {
    if (!enabled()) {
      return false;
    }
    Optional<User> manager = users.findByEmail(MANAGER.email());
    List<User> members =
        MEMBERS.stream().map(m -> users.findByEmail(m.email())).flatMap(Optional::stream).toList();
    if (manager.isEmpty() || members.size() != MEMBERS.size()) {
      return false;
    }
    if (!teams.findActiveByManagerId(manager.get().getId()).isEmpty()
        || members.stream().anyMatch(m -> teamMembers.findByUserId(m.getId()).isPresent())) {
      return false;
    }
    Optional<User> admin = users.findActiveByRoleSorted(Role.ADMIN).stream().findFirst();
    if (admin.isEmpty()) {
      LOG.warn("Équipe de démonstration non créée : aucun compte ADMIN actif.");
      return false;
    }
    Long adminId = admin.get().getId();
    // RG-00-04 : created_by / updated_by viennent de l'utilisateur courant ; on agit au nom de
    // l'ADMIN le temps de la création.
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            adminId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    SecurityContextHolder.setContext(context);
    try {
      teamService.create(
          adminId, TEAM_NAME, manager.get().getId(), members.stream().map(User::getId).toList());
      LOG.info("Équipe de démonstration « {} » créée.", TEAM_NAME);
      return true;
    } catch (RuntimeException e) {
      LOG.warn("Équipe de démonstration non créée : {}", e.getMessage());
      return false;
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
