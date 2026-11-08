package com.apptracer.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.ResourceNotFoundException;
import com.apptracer.team.domain.Team;
import com.apptracer.team.domain.TeamMember;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import com.apptracer.team.service.TeamService;
import com.apptracer.team.service.TeamService.TeamDetails;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class TeamUpdateServiceTest {

  private static final Long ADMIN_ID = 1L;
  private static final Long TEAM_ID = 50L;
  private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

  private final TeamRepository teams = mock(TeamRepository.class);
  private final TeamMemberRepository members = mock(TeamMemberRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final TeamService service =
      new TeamService(teams, members, users, Clock.fixed(NOW, ZoneOffset.UTC));

  private final List<User> known = new ArrayList<>();
  private User manager;
  private User alice;
  private User bob;
  private Team team;
  private final List<TeamMember> current = new ArrayList<>();

  private static void set(Object target, String name, Object value) {
    try {
      var f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private User account(Long id, String first, String last, Role role) {
    User u = new User("u" + id + "@example.com", first, last, "hash", role);
    set(u, "id", id);
    known.add(u);
    return u;
  }

  @BeforeEach
  void setUp() {
    manager = account(10L, "Mia", "Manager", Role.MANAGER);
    alice = account(11L, "Alice", "Aa", Role.USER);
    bob = account(12L, "Bob", "Bb", Role.USER);
    team = new Team("Produit", manager);
    set(team, "id", TEAM_ID);
    current.add(new TeamMember(alice.getId(), TEAM_ID));
    set(current.get(0), "user", alice);

    when(teams.findActiveById(TEAM_ID)).thenReturn(Optional.of(team));
    when(members.findByTeamIdsSorted(anyCollection())).thenReturn(current);
    when(members.findWithTeamByUserIds(anyCollection()))
        .thenAnswer(
            i ->
                current.stream()
                    .filter(
                        m -> ((java.util.Collection<?>) i.getArgument(0)).contains(m.getUserId()))
                    .toList());
    when(users.findAllByIdForShare(anyCollection()))
        .thenAnswer(
            i -> {
              java.util.Collection<Long> ids = i.getArgument(0);
              return known.stream().filter(u -> ids.contains(u.getId())).toList();
            });
    when(teams.saveAndFlush(any(Team.class))).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  @DisplayName("RG-05-08 : une équipe inconnue ou supprimée est introuvable (404)")
  void unknownTeam() {
    when(teams.findActiveById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.update(ADMIN_ID, 999L, "Produit", manager.getId(), List.of(), 0))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("Modification concurrente : une version périmée est refusée")
  void staleVersion() {
    assertThatThrownBy(
            () -> service.update(ADMIN_ID, TEAM_ID, "Produit", manager.getId(), List.of(), 3))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  @Test
  @DisplayName("RG-05-01 : renommer en un nom déjà pris par une autre équipe est refusé")
  void renameToExistingNameRefused() {
    when(teams.existsActiveByName("Technique", TEAM_ID)).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.update(
                    ADMIN_ID, TEAM_ID, "Technique", manager.getId(), List.of(alice.getId()), 0))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getRgCode()).isEqualTo("RG-05-01"));
  }

  @Test
  @DisplayName("RG-05-01 : la recherche du nom exclut l'équipe elle-même (pas de faux conflit)")
  void nameCheckExcludesCurrentTeam() {
    service.update(ADMIN_ID, TEAM_ID, "Produit", manager.getId(), List.of(alice.getId()), 0);

    verify(teams).existsActiveByName("Produit", TEAM_ID);
  }

  @Test
  @DisplayName("RG-05-02 : un nouveau manager de rôle USER est refusé")
  void newManagerMustBeManager() {
    assertThatThrownBy(
            () ->
                service.update(
                    ADMIN_ID, TEAM_ID, "Produit", bob.getId(), List.of(alice.getId()), 0))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getRgCode()).isEqualTo("RG-05-02"));
  }

  @Test
  @DisplayName("RG-05-02 : le manager est remplacé par un autre manager actif")
  void managerReplaced() {
    User other = account(13L, "Max", "Autre", Role.MANAGER);

    TeamDetails result =
        service.update(ADMIN_ID, TEAM_ID, "Produit", other.getId(), List.of(alice.getId()), 0);

    assertThat(result.team().getManager()).isSameAs(other);
  }

  @Test
  @DisplayName("RG-05-03 : un membre ajouté de rôle ADMIN est refusé")
  void adminMemberRefused() {
    User admin = account(14L, "Adam", "Admin", Role.ADMIN);

    assertThatThrownBy(
            () ->
                service.update(
                    ADMIN_ID, TEAM_ID, "Produit", manager.getId(), List.of(admin.getId()), 0))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getRgCode()).isEqualTo("RG-05-03"));
  }

  @Test
  @DisplayName("RG-05-05 : le nouveau manager ne peut pas rester membre de l'équipe")
  void newManagerCannotStayMember() {
    User other = account(13L, "Max", "Autre", Role.MANAGER);
    current.add(new TeamMember(other.getId(), TEAM_ID));

    assertThatThrownBy(
            () ->
                service.update(
                    ADMIN_ID, TEAM_ID, "Produit", other.getId(), List.of(other.getId()), 0))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getRgCode()).isEqualTo("RG-05-05"));
  }

  @Test
  @DisplayName("RG-05-04 : un membre déjà dans cette équipe n'est pas un conflit")
  void keptMemberIsNotAConflict() {
    TeamDetails result =
        service.update(ADMIN_ID, TEAM_ID, "Produit", manager.getId(), List.of(alice.getId()), 0);

    assertThat(result.members()).containsExactly(alice);
    verify(members, never()).deleteAll(anyList());
  }

  @Test
  @DisplayName("RG-05-04 : un membre d'une autre équipe est refusé, avec le nom de cette équipe")
  void memberOfOtherTeamRefused() {
    Team other = new Team("Technique", manager);
    set(other, "id", 77L);
    TeamMember link = new TeamMember(bob.getId(), 77L);
    set(link, "team", other);
    current.add(link);
    // le lien de bob est en base dans l'équipe 77, pas dans l'équipe 50
    when(members.findByTeamIdsSorted(anyCollection())).thenReturn(List.of(current.get(0)));
    when(members.findWithTeamByUserIds(anyCollection())).thenReturn(List.of(link));

    assertThatThrownBy(
            () ->
                service.update(
                    ADMIN_ID,
                    TEAM_ID,
                    "Produit",
                    manager.getId(),
                    List.of(alice.getId(), bob.getId()),
                    0))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getRgCode()).isEqualTo("RG-05-04");
              assertThat(e.getMessage()).isEqualTo("error.team.member-already-in-team");
              assertThat(e.getArgs()).contains("Technique");
            });
  }

  @Test
  @DisplayName("RG-05-07 : la liste complète remplace les membres, elle peut être vide")
  void replaceMembersWithEmptyList() {
    service.update(ADMIN_ID, TEAM_ID, "Produit", manager.getId(), List.of(), 0);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<TeamMember>> removed = ArgumentCaptor.forClass(List.class);
    verify(members).deleteAll(removed.capture());
    assertThat(removed.getValue()).extracting(TeamMember::getUserId).containsExactly(alice.getId());
  }

  @Test
  @DisplayName(
      "RG-05-04 : les membres conservés gardent leur ligne, seuls les nouveaux en reçoivent")
  void onlyNewMembersGetARow() {
    service.update(
        ADMIN_ID, TEAM_ID, "Produit", manager.getId(), List.of(alice.getId(), bob.getId()), 0);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<TeamMember>> added = ArgumentCaptor.forClass(List.class);
    verify(members).saveAll(added.capture());
    assertThat(added.getValue()).extracting(TeamMember::getUserId).containsExactly(bob.getId());
    verify(members, never()).deleteAll(anyList());
  }

  @Test
  @DisplayName("RG-05-14 : un changement de membres met à jour la date et l'auteur de l'équipe")
  void membershipChangeTouchesTeam() {
    service.update(
        ADMIN_ID, TEAM_ID, "Produit", manager.getId(), List.of(alice.getId(), bob.getId()), 0);

    assertThat(team.getUpdatedAt()).isEqualTo(NOW);
    assertThat(team.getUpdatedBy()).isEqualTo(ADMIN_ID);
  }

  @Test
  @DisplayName("RG-05-14 : sans changement de membres, l'équipe n'est pas marquée modifiée")
  void noMembershipChangeDoesNotTouch() {
    service.update(ADMIN_ID, TEAM_ID, "Produit", manager.getId(), List.of(alice.getId()), 0);

    assertThat(team.getUpdatedAt()).isNull();
  }
}
