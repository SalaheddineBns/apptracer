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
 * Outil de développement (hors SFD) : crée au démarrage un manager, un développeur et un QA de
 * démonstration, puis l'équipe « Demo team » qui les réunit, seulement si DEMO_PASSWORD est défini
 * (jamais en production). Idempotent : un e-mail déjà présent est ignoré, aucun compte existant
 * n'est modifié, et l'équipe n'est créée que si le manager n'en dirige aucune et que les deux
 * membres n'en ont pas. L'ADMIN vient de {@link InitialAdminInitializer}, exécuté avant. Le mot de
 * passe n'apparaît dans aucun message ni log.
 */
@Component
@Order(1)
@EnableConfigurationProperties(DemoAccountsProperties.class)
public class DemoAccountsInitializer implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(DemoAccountsInitializer.class);

  /** Compte de démonstration ; {@code jobTitleCode} vaut {@code null} pour aucune fonction. */
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

  /** Renvoie le nombre de comptes créés. */
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
   * Crée « Demo team » (manager de démonstration, dev et QA comme membres) avec les règles de
   * SFD-05, au nom du premier ADMIN actif. Renvoie vrai si l'équipe a été créée.
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
