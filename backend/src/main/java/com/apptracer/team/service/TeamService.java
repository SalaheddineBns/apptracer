package com.apptracer.team.service;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.InvalidRequestException;
import com.apptracer.common.error.ResourceNotFoundException;
import com.apptracer.referential.domain.Project;
import com.apptracer.referential.repository.ProjectRepository;
import com.apptracer.team.domain.Team;
import com.apptracer.team.domain.TeamMember;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Team management (SFD-05). RG-05-08: ADMIN only, checked by the controller. Manager and member
 * accounts are loaded with a shared lock (architecture §2.11) so that their checks (RG-05-02,
 * RG-05-03) are not contradicted by a concurrent change of the account.
 */
@Service
public class TeamService {

  /** Team and its members, sorted by last name then first name (RG-05-13). */
  public record TeamDetails(Team team, List<User> members) {}

  /** Account eligible as a member, with its current team (RG-05-04), if any. */
  public record MemberCandidate(User user, Team currentTeam) {}

  /** RG-05-13: members sorted by last name then first name. */
  static final Comparator<User> MEMBER_ORDER =
      Comparator.comparing((User u) -> u.getLastName().toLowerCase())
          .thenComparing(u -> u.getFirstName().toLowerCase())
          .thenComparing(User::getId);

  private static final int NAME_MIN = 2;
  private static final int NAME_MAX = 100;
  private static final Long NO_TEAM = -1L;

  private final TeamRepository teams;
  private final TeamMemberRepository members;
  private final UserRepository users;
  private final Clock clock;
  private final ProjectRepository projects;

  public TeamService(
      TeamRepository teams,
      TeamMemberRepository members,
      UserRepository users,
      Clock clock,
      ProjectRepository projects) {
    this.projects = projects;
    this.teams = teams;
    this.members = members;
    this.users = users;
    this.clock = clock;
  }

  /**
   * Creates a team. Order of the checks: RG-05-01, RG-05-02, RG-05-03, RG-05-05, RG-05-04.
   * RG-05-07: {@code memberIds} may be empty. RG-05-06: one manager can lead several teams. {@code
   * actorId} is the signed-in ADMIN, read from the token.
   */
  @Transactional
  public TeamDetails create(Long actorId, String name, Long managerId, List<Long> memberIds) {
    String teamName = normalizeName(name);
    checkNameAvailable(teamName, NO_TEAM);
    Set<Long> ids = distinct(memberIds);
    Checked checked = checkManagerAndMembers(managerId, ids, NO_TEAM);

    // RG-00-04, RG-05-14: created_by / updated_by filled in by JPA auditing
    Team team = teams.saveAndFlush(new Team(teamName, checked.manager()));
    // RG-05-04: the primary key of team_members covers concurrent creations
    members.saveAllAndFlush(ids.stream().map(id -> new TeamMember(id, team.getId())).toList());
    return new TeamDetails(team, sorted(checked.members()));
  }

  /**
   * Replaces the name, the manager and the full list of members in one go, with the same checks as
   * at creation (except RG-05-04 for members already in this team). Kept members keep their link,
   * removed ones lose it, new ones get one. RG-05-14: any change of manager or members updates the
   * team (date, author, version).
   */
  @Transactional
  public TeamDetails update(
      Long actorId, Long id, String name, Long managerId, List<Long> memberIds, long version) {
    // RG-05-09: a deleted team cannot be found
    Team team =
        teams
            .findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.team.not-found"));
    // Concurrent change: the version read by the ADMIN is no longer the version in the database
    if (team.getVersion() != version) {
      throw new ObjectOptimisticLockingFailureException(Team.class, id);
    }
    String teamName = normalizeName(name);
    checkNameAvailable(teamName, id);
    Set<Long> ids = distinct(memberIds);
    Checked checked = checkManagerAndMembers(managerId, ids, id);

    List<TeamMember> current = members.findByTeamIdsSorted(List.of(id));
    Set<Long> currentIds = current.stream().map(TeamMember::getUserId).collect(Collectors.toSet());
    List<TeamMember> removed = current.stream().filter(m -> !ids.contains(m.getUserId())).toList();
    List<TeamMember> added =
        ids.stream().filter(u -> !currentIds.contains(u)).map(u -> new TeamMember(u, id)).toList();

    team.setName(teamName);
    team.setManager(checked.manager());
    if (!removed.isEmpty() || !added.isEmpty()) {
      // RG-05-14: a change of members records the change on the team itself
      team.touch(Instant.now(clock), actorId);
    }
    if (!removed.isEmpty()) {
      members.deleteAll(removed);
      members.flush();
    }
    // RG-05-04: the primary key of team_members covers concurrent changes
    members.saveAll(added);
    // RG-00-04: updated_at / updated_by filled in by JPA auditing
    Team saved = teams.saveAndFlush(team);
    return new TeamDetails(saved, sorted(checked.members()));
  }

  /**
   * RG-05-09: permanent deletion. The links disappear and the members end up without a team; their
   * accounts and time entries are not touched. RG-05-14: the team's row is kept, marked as deleted
   * (who, when), and its name becomes available again. RG-10-07: its projects no longer have a team
   * (RG-10-10: recorded by JPA auditing).
   */
  @Transactional
  public void delete(Long actorId, Long id) {
    Team team =
        teams
            .findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.team.not-found"));
    members.deleteByTeamId(id);
    List<Project> teamProjects = projects.findByTeamId(id);
    teamProjects.forEach(project -> project.setTeam(null));
    projects.saveAllAndFlush(teamProjects);
    team.markDeleted(actorId, Instant.now(clock));
    teams.saveAndFlush(team);
  }

  @Transactional(readOnly = true)
  public TeamDetails get(Long id) {
    // RG-05-09: a deleted team cannot be found
    Team team =
        teams
            .findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.team.not-found"));
    List<User> teamMembers =
        members.findByTeamIdsSorted(List.of(id)).stream().map(TeamMember::getUser).toList();
    return new TeamDetails(team, teamMembers);
  }

  /** RG-05-13: teams sorted by name, members sorted by last name then first name. */
  @Transactional(readOnly = true)
  public List<TeamDetails> list() {
    List<Team> all = teams.findAllActiveSorted();
    if (all.isEmpty()) {
      return List.of();
    }
    Map<Long, List<User>> byTeam =
        members.findByTeamIdsSorted(all.stream().map(Team::getId).toList()).stream()
            .collect(
                Collectors.groupingBy(
                    TeamMember::getTeamId,
                    Collectors.mapping(TeamMember::getUser, Collectors.toList())));
    return all.stream()
        .map(t -> new TeamDetails(t, byTeam.getOrDefault(t.getId(), List.of())))
        .toList();
  }

  /** RG-05-02: active accounts with the MANAGER role, sorted by last name then first name. */
  @Transactional(readOnly = true)
  public List<User> managerCandidates() {
    return users.findActiveByRoleSorted(Role.MANAGER);
  }

  /**
   * RG-05-03: USER or MANAGER accounts, active or not (RG-05-11), never ADMIN. RG-05-04: each one
   * with its current team, which the API will refuse to remove implicitly (H10).
   */
  @Transactional(readOnly = true)
  public List<MemberCandidate> memberCandidates() {
    Map<Long, Team> currentTeams =
        members.findAllWithTeam().stream()
            .collect(Collectors.toMap(TeamMember::getUserId, TeamMember::getTeam));
    return users.findByRolesSorted(List.of(Role.USER, Role.MANAGER)).stream()
        .map(u -> new MemberCandidate(u, currentTeams.get(u.getId())))
        .toList();
  }

  // ---- checks shared by creation and update ----

  /** RG-05-01: 2 to 100 characters, surrounding spaces removed. */
  private static String normalizeName(String name) {
    String stripped = name == null ? "" : name.strip();
    if (stripped.length() < NAME_MIN || stripped.length() > NAME_MAX) {
      throw new InvalidRequestException(
          "error.validation.detail", "name", "validation.team.name.size");
    }
    return stripped;
  }

  /** RG-05-01: unique name, case-insensitive, among the teams that are not deleted. */
  private void checkNameAvailable(String name, Long currentTeamId) {
    if (teams.existsActiveByName(name, currentTeamId)) {
      throw BusinessException.onField(
          "RG-05-01", "error.team.name-duplicate", "name", "field.team.name-duplicate");
    }
  }

  private static Set<Long> distinct(List<Long> ids) {
    return new LinkedHashSet<>(ids);
  }

  private record Checked(User manager, List<User> members) {}

  private Checked checkManagerAndMembers(Long managerId, Set<Long> memberIds, Long currentTeamId) {
    Set<Long> all = new LinkedHashSet<>(memberIds);
    all.add(managerId);
    // shared lock on the manager and the members (architecture §2.11)
    Map<Long, User> accounts =
        users.findAllByIdForShare(all).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

    // RG-05-02: an active manager with the MANAGER role (an unknown identifier is not eligible)
    User manager = accounts.get(managerId);
    if (manager == null || !manager.isActive() || manager.getRole() != Role.MANAGER) {
      throw BusinessException.onField(
          "RG-05-02",
          "error.team.manager-eligibility",
          "managerId",
          "field.team.manager-eligibility");
    }

    // RG-05-03: USER or MANAGER members, never ADMIN (an unknown identifier is not eligible)
    List<User> memberAccounts = new ArrayList<>();
    for (Long id : memberIds) {
      User member = accounts.get(id);
      if (member == null || member.getRole() == Role.ADMIN) {
        throw BusinessException.onField(
            "RG-05-03",
            "error.team.member-eligibility",
            "memberIds",
            "field.team.member-eligibility");
      }
      memberAccounts.add(member);
    }

    // RG-05-05: a team's manager is not one of its members
    if (memberIds.contains(managerId)) {
      throw BusinessException.onField(
          "RG-05-05",
          "error.team.manager-not-member",
          "memberIds",
          "field.team.manager-not-member");
    }

    // RG-05-04: at most one team per account, no implicit transfer (H10)
    if (!memberIds.isEmpty()) {
      Map<Long, TeamMember> existing =
          members.findWithTeamByUserIds(memberIds).stream()
              .collect(Collectors.toMap(TeamMember::getUserId, Function.identity()));
      for (User member : memberAccounts) {
        TeamMember link = existing.get(member.getId());
        if (link != null && !link.getTeamId().equals(currentTeamId)) {
          throw BusinessException.onField(
              "RG-05-04",
              "error.team.member-already-in-team",
              "memberIds",
              "field.team.member-already-in-team",
              fullName(member),
              link.getTeam().getName());
        }
      }
    }
    return new Checked(manager, memberAccounts);
  }

  private static String fullName(User user) {
    return user.getFirstName() + " " + user.getLastName();
  }

  private static List<User> sorted(Collection<User> accounts) {
    return accounts.stream().sorted(MEMBER_ORDER).toList();
  }
}
