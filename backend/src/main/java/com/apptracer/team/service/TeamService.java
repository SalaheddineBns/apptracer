package com.apptracer.team.service;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.InvalidRequestException;
import com.apptracer.common.error.ResourceNotFoundException;
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
 * Gestion des équipes (SFD-05). RG-05-08 : réservé à l'ADMIN, contrôlé par le contrôleur. Les
 * comptes manager et membres sont chargés avec un verrou partagé (architecture §2.11) pour que
 * leurs contrôles (RG-05-02, RG-05-03) ne soient pas contredits par une modification concurrente du
 * compte.
 */
@Service
public class TeamService {

  /** Équipe et ses membres, triés par nom puis prénom (RG-05-13). */
  public record TeamDetails(Team team, List<User> members) {}

  /** Compte éligible comme membre, avec son équipe actuelle (RG-05-04), s'il en a une. */
  public record MemberCandidate(User user, Team currentTeam) {}

  /** RG-05-13 : membres triés par nom puis prénom. */
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

  public TeamService(
      TeamRepository teams, TeamMemberRepository members, UserRepository users, Clock clock) {
    this.teams = teams;
    this.members = members;
    this.users = users;
    this.clock = clock;
  }

  /**
   * Crée une équipe. Ordre des contrôles : RG-05-01, RG-05-02, RG-05-03, RG-05-05, RG-05-04.
   * RG-05-07 : {@code memberIds} peut être vide. RG-05-06 : un même manager peut avoir plusieurs
   * équipes. {@code actorId} est l'ADMIN connecté, lu dans le token.
   */
  @Transactional
  public TeamDetails create(Long actorId, String name, Long managerId, List<Long> memberIds) {
    String teamName = normalizeName(name);
    checkNameAvailable(teamName, NO_TEAM);
    Set<Long> ids = distinct(memberIds);
    Checked checked = checkManagerAndMembers(managerId, ids, NO_TEAM);

    // RG-00-04, RG-05-14 : created_by / updated_by renseignés par l'audit JPA
    Team team = teams.saveAndFlush(new Team(teamName, checked.manager()));
    // RG-05-04 : la clé primaire de team_members couvre les créations concurrentes
    members.saveAllAndFlush(ids.stream().map(id -> new TeamMember(id, team.getId())).toList());
    return new TeamDetails(team, sorted(checked.members()));
  }

  /**
   * Remplace d'un bloc le nom, le manager et la liste complète des membres, avec les mêmes
   * contrôles qu'à la création (hors RG-05-04 pour les membres déjà dans cette équipe). Les membres
   * conservés gardent leur rattachement, les retirés le perdent, les nouveaux en reçoivent un.
   * RG-05-14 : tout changement de manager ou de membres met à jour l'équipe (date, auteur,
   * version).
   */
  @Transactional
  public TeamDetails update(
      Long actorId, Long id, String name, Long managerId, List<Long> memberIds, long version) {
    // RG-05-09 : une équipe supprimée est introuvable
    Team team =
        teams
            .findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.team.not-found"));
    // Modification concurrente : la version lue par l'ADMIN n'est plus la version en base
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
      // RG-05-14 : un changement de membres trace la modification sur l'équipe elle-même
      team.touch(Instant.now(clock), actorId);
    }
    if (!removed.isEmpty()) {
      members.deleteAll(removed);
      members.flush();
    }
    // RG-05-04 : la clé primaire de team_members couvre les modifications concurrentes
    members.saveAll(added);
    // RG-00-04 : updated_at / updated_by renseignés par l'audit JPA
    Team saved = teams.saveAndFlush(team);
    return new TeamDetails(saved, sorted(checked.members()));
  }

  /**
   * RG-05-09 : suppression définitive. Les rattachements disparaissent, les membres se retrouvent
   * sans équipe ; leurs comptes et leurs saisies ne sont pas touchés. RG-05-14 : la ligne de
   * l'équipe est conservée, marquée supprimée (qui, quand), et son nom redevient disponible.
   */
  @Transactional
  public void delete(Long actorId, Long id) {
    Team team =
        teams
            .findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.team.not-found"));
    members.deleteByTeamId(id);
    team.markDeleted(actorId, Instant.now(clock));
    teams.saveAndFlush(team);
  }

  @Transactional(readOnly = true)
  public TeamDetails get(Long id) {
    // RG-05-09 : une équipe supprimée est introuvable
    Team team =
        teams
            .findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("error.team.not-found"));
    List<User> teamMembers =
        members.findByTeamIdsSorted(List.of(id)).stream().map(TeamMember::getUser).toList();
    return new TeamDetails(team, teamMembers);
  }

  /** RG-05-13 : équipes triées par nom, membres triés par nom puis prénom. */
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

  /** RG-05-02 : comptes actifs de rôle MANAGER, triés par nom puis prénom. */
  @Transactional(readOnly = true)
  public List<User> managerCandidates() {
    return users.findActiveByRoleSorted(Role.MANAGER);
  }

  /**
   * RG-05-03 : comptes USER ou MANAGER, actifs ou non (RG-05-11), jamais ADMIN. RG-05-04 : chacun
   * avec son équipe actuelle, que l'API refusera de lui retirer implicitement (H10).
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

  // ---- contrôles communs à la création et à la modification ----

  /** RG-05-01 : 2 à 100 caractères, espaces de bord retirés. */
  private static String normalizeName(String name) {
    String stripped = name == null ? "" : name.strip();
    if (stripped.length() < NAME_MIN || stripped.length() > NAME_MAX) {
      throw new InvalidRequestException(
          "error.validation.detail", "name", "validation.team.name.size");
    }
    return stripped;
  }

  /** RG-05-01 : nom unique, insensible à la casse, parmi les équipes non supprimées. */
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
    // verrou partagé sur le manager et les membres (architecture §2.11)
    Map<Long, User> accounts =
        users.findAllByIdForShare(all).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

    // RG-05-02 : un manager actif de rôle MANAGER (un identifiant inconnu n'est pas éligible)
    User manager = accounts.get(managerId);
    if (manager == null || !manager.isActive() || manager.getRole() != Role.MANAGER) {
      throw BusinessException.onField(
          "RG-05-02",
          "error.team.manager-eligibility",
          "managerId",
          "field.team.manager-eligibility");
    }

    // RG-05-03 : membres USER ou MANAGER, jamais ADMIN (un identifiant inconnu n'est pas éligible)
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

    // RG-05-05 : le manager d'une équipe n'en est pas membre
    if (memberIds.contains(managerId)) {
      throw BusinessException.onField(
          "RG-05-05",
          "error.team.manager-not-member",
          "memberIds",
          "field.team.manager-not-member");
    }

    // RG-05-04 : au plus une équipe par compte, pas de transfert implicite (H10)
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
