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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  public TeamService(TeamRepository teams, TeamMemberRepository members, UserRepository users) {
    this.teams = teams;
    this.members = members;
    this.users = users;
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

  @Transactional(readOnly = true)
  public TeamDetails get(Long id) {
    // RG-05-09 : une équipe supprimée est introuvable
    Team team =
        teams
            .findActiveById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Équipe introuvable."));
    List<User> teamMembers =
        members.findByTeamIdsSorted(List.of(id)).stream().map(TeamMember::getUser).toList();
    return new TeamDetails(team, teamMembers);
  }

  // ---- contrôles communs à la création et à la modification ----

  /** RG-05-01 : 2 à 100 caractères, espaces de bord retirés. */
  private static String normalizeName(String name) {
    String stripped = name == null ? "" : name.strip();
    if (stripped.length() < NAME_MIN || stripped.length() > NAME_MAX) {
      throw new InvalidRequestException(
          "La requête est invalide. Corrigez les champs signalés.",
          "name",
          "Le nom doit contenir de 2 à 100 caractères.");
    }
    return stripped;
  }

  /** RG-05-01 : nom unique, insensible à la casse, parmi les équipes non supprimées. */
  private void checkNameAvailable(String name, Long currentTeamId) {
    if (teams.existsActiveByName(name, currentTeamId)) {
      throw BusinessException.onField(
          "RG-05-01",
          "Une équipe existe déjà avec ce nom.",
          "name",
          "Ce nom d'équipe est déjà utilisé.");
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
          "Le manager d'une équipe doit être un compte actif de rôle MANAGER.",
          "managerId",
          "Choisissez un compte actif de rôle MANAGER.");
    }

    // RG-05-03 : membres USER ou MANAGER, jamais ADMIN (un identifiant inconnu n'est pas éligible)
    List<User> memberAccounts = new ArrayList<>();
    for (Long id : memberIds) {
      User member = accounts.get(id);
      if (member == null || member.getRole() == Role.ADMIN) {
        throw BusinessException.onField(
            "RG-05-03",
            "Un ADMIN ou un compte inconnu ne peut pas être membre d'une équipe.",
            "memberIds",
            "Les membres doivent être des comptes de rôle USER ou MANAGER.");
      }
      memberAccounts.add(member);
    }

    // RG-05-05 : le manager d'une équipe n'en est pas membre
    if (memberIds.contains(managerId)) {
      throw BusinessException.onField(
          "RG-05-05",
          "Le manager d'une équipe n'en est pas membre.",
          "memberIds",
          "Le manager ne peut pas être membre de sa propre équipe.");
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
              "Le compte « %s » est déjà membre de l'équipe « %s »."
                  .formatted(fullName(member), link.getTeam().getName()),
              "memberIds",
              "%s est déjà membre de l'équipe « %s »."
                  .formatted(fullName(member), link.getTeam().getName()));
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
