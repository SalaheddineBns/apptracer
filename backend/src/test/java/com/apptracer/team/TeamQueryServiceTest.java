package com.apptracer.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.team.domain.Team;
import com.apptracer.team.domain.TeamMember;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import com.apptracer.team.service.TeamService;
import com.apptracer.team.service.TeamService.MemberCandidate;
import com.apptracer.team.service.TeamService.TeamDetails;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeamQueryServiceTest {

  private final TeamRepository teams = mock(TeamRepository.class);
  private final TeamMemberRepository members = mock(TeamMemberRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final TeamService service = new TeamService(teams, members, users);

  private static void set(Object target, String name, Object value) {
    try {
      var f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static User account(Long id, String first, String last, Role role, boolean active) {
    User u = new User("u" + id + "@example.com", first, last, "hash", role);
    u.setActive(active);
    set(u, "id", id);
    return u;
  }

  private static Team team(Long id, String name, User manager) {
    Team t = new Team(name, manager);
    set(t, "id", id);
    return t;
  }

  private static TeamMember link(User user, Team team) {
    TeamMember m = new TeamMember(user.getId(), team.getId());
    set(m, "user", user);
    set(m, "team", team);
    return m;
  }

  @Test
  @DisplayName("RG-05-13 : listTeams reprend l'ordre du dépôt (par nom) et groupe les membres")
  void listGroupsMembersByTeamInRepositoryOrder() {
    User m = account(1L, "Mia", "Manager", Role.MANAGER, true);
    User anne = account(2L, "Anne", "Adam", Role.USER, true);
    User zoe = account(3L, "Zoé", "Zola", Role.USER, true);
    User paul = account(4L, "Paul", "Petit", Role.USER, true);
    Team a = team(10L, "Alpha", m);
    Team b = team(11L, "Bravo", m);
    when(teams.findAllActiveSorted()).thenReturn(List.of(a, b));
    when(members.findByTeamIdsSorted(anyCollection()))
        .thenReturn(List.of(link(anne, a), link(zoe, a), link(paul, b)));

    List<TeamDetails> result = service.list();

    assertThat(result).extracting(d -> d.team().getName()).containsExactly("Alpha", "Bravo");
    assertThat(result.get(0).members()).containsExactly(anne, zoe);
    assertThat(result.get(1).members()).containsExactly(paul);
  }

  @Test
  @DisplayName("RG-05-07 : une équipe sans membre est listée avec une liste de membres vide")
  void listTeamWithoutMembers() {
    User m = account(1L, "Mia", "Manager", Role.MANAGER, true);
    when(teams.findAllActiveSorted()).thenReturn(List.of(team(10L, "Vide", m)));
    when(members.findByTeamIdsSorted(anyCollection())).thenReturn(List.of());

    assertThat(service.list().get(0).members()).isEmpty();
  }

  @Test
  @DisplayName("RG-05-13 : aucune équipe donne une liste vide sans lire les membres")
  void listNoTeams() {
    when(teams.findAllActiveSorted()).thenReturn(List.of());

    assertThat(service.list()).isEmpty();
  }

  @Test
  @DisplayName("RG-05-02 : seuls les comptes actifs de rôle MANAGER sont candidats managers")
  void managerCandidatesAreActiveManagers() {
    User m = account(1L, "Mia", "Manager", Role.MANAGER, true);
    when(users.findActiveByRoleSorted(Role.MANAGER)).thenReturn(List.of(m));

    assertThat(service.managerCandidates()).containsExactly(m);
    verify(users).findActiveByRoleSorted(Role.MANAGER);
  }

  @Test
  @DisplayName("RG-05-03 : les candidats membres sont demandés pour USER et MANAGER, jamais ADMIN")
  void memberCandidatesExcludeAdmins() {
    when(members.findAllWithTeam()).thenReturn(List.of());
    when(users.findByRolesSorted(anyCollection())).thenReturn(List.of());

    service.memberCandidates();

    verify(users).findByRolesSorted(List.of(Role.USER, Role.MANAGER));
  }

  @Test
  @DisplayName("RG-05-04 / RG-05-11 : chaque candidat porte son équipe actuelle, inactifs inclus")
  void memberCandidatesCarryCurrentTeam() {
    User m = account(1L, "Mia", "Manager", Role.MANAGER, true);
    User member = account(2L, "Anne", "Adam", Role.USER, false);
    User free = account(3L, "Zoé", "Zola", Role.USER, true);
    Team produit = team(10L, "Produit", m);
    when(members.findAllWithTeam()).thenReturn(List.of(link(member, produit)));
    when(users.findByRolesSorted(anyCollection())).thenReturn(List.of(member, free));

    List<MemberCandidate> result = service.memberCandidates();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).user().isActive()).isFalse();
    assertThat(result.get(0).currentTeam()).isSameAs(produit);
    assertThat(result.get(1).currentTeam()).isNull();
  }
}
