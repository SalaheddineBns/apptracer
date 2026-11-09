package com.apptracer.account.service;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.referential.repository.ProjectRepository;
import com.apptracer.referential.service.ProjectService;
import com.apptracer.timeentry.domain.Granularity;
import com.apptracer.timeentry.repository.TimeEntryRepository;
import com.apptracer.timeentry.service.TimeEntryCommand;
import com.apptracer.timeentry.service.TimeEntryService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Outil de démonstration (hors SFD) : après {@link DemoAccountsInitializer}, remplit les 10 jours
 * ouvrés précédant aujourd'hui pour le développeur et le QA de démonstration, et crée le projet «
 * AppTracer » qu'utilisent ces saisies. Seulement si DEMO_PASSWORD est défini. Les saisies passent
 * par {@link TimeEntryService}, au nom de leur auteur, avec toutes les règles de SFD-01. Un compte
 * qui a déjà une saisie est ignoré.
 */
@Component
@Order(2)
public class DemoWeekSeeder implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(DemoWeekSeeder.class);

  public static final String PROJECT_CODE = "APPTRACER";
  static final int WORKING_DAYS = 10;

  private record Slot(
      String start, String end, String activityTypeCode, boolean project, String comment) {}

  private static final List<Slot> DEVELOPER_DAY =
      List.of(
          new Slot("09:00", "09:15", "REUNION", false, "Daily stand-up"),
          new Slot("09:15", "12:30", "TRAVAIL_PROJET", true, "Team view: weekly totals"),
          new Slot("13:30", "15:00", "REVUE_MR", true, "Review of the job titles merge request"),
          new Slot("15:00", "17:30", "TRAVAIL_PROJET", true, "Fix time picker quarter hours"));
  private static final List<Slot> DEVELOPER_FRIDAY =
      List.of(
          new Slot("09:00", "09:15", "REUNION", false, "Daily stand-up"),
          new Slot("09:15", "12:00", "FORMATION", false, "Angular signals workshop"),
          new Slot("13:30", "17:00", "TRAVAIL_PROJET", true, "Refactor the journal store"));
  private static final List<Slot> QA_DAY =
      List.of(
          new Slot("09:00", "09:15", "REUNION", false, "Daily stand-up"),
          new Slot("09:15", "10:30", "LECTURE_SFD", false, "Read SFD-09 job titles"),
          new Slot("10:30", "12:30", "TRAVAIL_PROJET", true, "Test plan for the team view"),
          new Slot("13:30", "17:00", "TRAVAIL_PROJET", true, "Regression tests on the journal"));

  private final DemoAccountsProperties properties;
  private final UserRepository users;
  private final ProjectRepository projects;
  private final ProjectService projectService;
  private final TimeEntryRepository entries;
  private final TimeEntryService entryService;
  private final Clock clock;

  public DemoWeekSeeder(
      DemoAccountsProperties properties,
      UserRepository users,
      ProjectRepository projects,
      ProjectService projectService,
      TimeEntryRepository entries,
      TimeEntryService entryService,
      Clock clock) {
    this.properties = properties;
    this.users = users;
    this.projects = projects;
    this.projectService = projectService;
    this.entries = entries;
    this.entryService = entryService;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    seed();
  }

  /** Renvoie le nombre de saisies créées. */
  public int seed() {
    String password = properties.password();
    if (password == null || password.isBlank()) {
      return 0;
    }
    Optional<User> admin = users.findActiveByRoleSorted(Role.ADMIN).stream().findFirst();
    if (admin.isEmpty()) {
      return 0;
    }
    try {
      if (!projects.existsByCode(PROJECT_CODE)) {
        runAs(admin.get(), () -> projectService.create(PROJECT_CODE, "AppTracer"));
      }
      List<LocalDate> days = previousWorkingDays(LocalDate.now(clock), WORKING_DAYS);
      int created = 0;
      created += seedUser("dev@apptracer.local", days, true);
      created += seedUser("qa@apptracer.local", days, false);
      if (created > 0) {
        LOG.info("{} saisie(s) de démonstration créée(s).", created);
      }
      return created;
    } catch (RuntimeException e) {
      LOG.warn("Saisies de démonstration non créées : {}", e.getMessage());
      return 0;
    }
  }

  private int seedUser(String email, List<LocalDate> days, boolean developer) {
    Optional<User> user = users.findByEmail(email);
    if (user.isEmpty() || !user.get().isActive() || entries.existsByUserId(user.get().getId())) {
      return 0;
    }
    int[] created = {0};
    runAs(
        user.get(),
        () -> {
          for (LocalDate day : days) {
            List<Slot> slots =
                developer
                    ? (day.getDayOfWeek() == DayOfWeek.FRIDAY ? DEVELOPER_FRIDAY : DEVELOPER_DAY)
                    : QA_DAY;
            for (Slot slot : slots) {
              entryService.create(
                  user.get().getId(),
                  new TimeEntryCommand(
                      Granularity.HOUR,
                      day,
                      null,
                      LocalTime.parse(slot.start()),
                      LocalTime.parse(slot.end()),
                      slot.activityTypeCode(),
                      slot.project() ? PROJECT_CODE : null,
                      slot.comment()));
              created[0]++;
            }
          }
        });
    return created[0];
  }

  /**
   * Les {@code count} jours du lundi au vendredi strictement avant {@code today}, du plus ancien.
   */
  static List<LocalDate> previousWorkingDays(LocalDate today, int count) {
    List<LocalDate> days = new ArrayList<>();
    LocalDate day = today.minusDays(1);
    while (days.size() < count) {
      if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
        days.add(0, day);
      }
      day = day.minusDays(1);
    }
    return days;
  }

  /** RG-00-04 : created_by / updated_by viennent de l'utilisateur courant. */
  private static void runAs(User user, Runnable action) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            user.getId().toString(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    SecurityContextHolder.setContext(context);
    try {
      action.run();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
