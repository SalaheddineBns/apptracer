package com.apptracer.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.apptracer.team.service.TeamService;
import com.apptracer.team.service.TeamService.TeamDetails;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeamServiceTest {

  private static final Long ADMIN_ID = 1L;

  private final TeamRepository teams = mock(TeamRepository.class);
  private final TeamMemberRepository members = mock(TeamMemberRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final TeamService service = new TeamService(teams, members, users);

  private final List<User> known = new ArrayList<>();

  @BeforeEach
  void setUp() {
    when(users.findAllByIdForShare(anyCollection()))
        .thenAnswer(
            i -> {
              java.util.Collection<Long> ids = i.getArgument(0);
              return known.stream().filter(u -> ids.contains(u.getId())).toList();
            });
    when(teams.saveAndFlush(any(Team.class)))
        .thenAnswer(
            i -> {
              Team t = i.getArgument(0);
              setId(t, Team.class, 100L);
              return t;
            });
    when(members.saveAllAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    when(members.findWithTeamByUserIds(anyCollection())).thenReturn(List.of());
  }

  private static void setId(Object target, Class<?> type, Long id) {
    try {
      var f = type.getDeclaredField("id");
      f.setAccessible(true);
      f.set(target, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private User account(Long id, String first, String last, Role role, boolean active) {
    User u = new User("u" + id + "@example.com", first, last, "hash", role);
    u.setActive(active);
    setId(u, User.class, id);
    known.add(u);
    return u;
  }

  private User manager() {
    return account(10L, "Mia", "Manager", Role.MANAGER, true);
  }

  @Test
  @DisplayName("RG-05-01 : le nom est enregistré sans espaces de bord")
  void nameIsStripped() {
    User m = manager();

    TeamDetails created = service.create(ADMIN_ID, "  Produit  ", m.getId(), List.of());

    assertThat(created.team().getName()).isEqualTo("Produit");
  }

  @Test
  @DisplayName("RG-05-01 : un nom déjà utilisé (casse ignorée) est refusé sur le champ name")
  void duplicateNameRefused() {
    User m = manager();
    when(teams.existsActiveByName("PRODUIT", -1L)).thenReturn(true);

    assertThatThrownBy(() -> service.create(ADMIN_ID, "PRODUIT", m.getId(), List.of()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getRgCode()).isEqualTo("RG-05-01");
              assertThat(e.getErrors()).extracting("field").containsExactly("name");
            });
    verify(teams, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("RG-05-01 : un nom de moins de 2 ou plus de 100 caractères (après trim) est refusé")
  void nameLengthRefused() {
    User m = manager();

    assertThatThrownBy(() -> service.create(ADMIN_ID, " a ", m.getId(), List.of()))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.create(ADMIN_ID, "x".repeat(101), m.getId(), List.of()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @DisplayName("RG-05-02 : un manager de rôle USER est refusé (champ managerId)")
  void userCannotBeManager() {
    User u = account(11L, "Uma", "User", Role.USER, true);

    assertThatThrownBy(() -> service.create(ADMIN_ID, "Produit", u.getId(), List.of()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getRgCode()).isEqualTo("RG-05-02");
              assertThat(e.getErrors()).extracting("field").containsExactly("managerId");
            });
  }

  @Test
  @DisplayName("RG-05-02 : un manager inactif, un ADMIN ou un compte inconnu sont refusés")
  void inactiveAdminOrUnknownManagerRefused() {
    User inactive = account(12L, "Ines", "Inactive", Role.MANAGER, false);
    User admin = account(13L, "Adam", "Admin", Role.ADMIN, true);

    for (Long id : List.of(inactive.getId(), admin.getId(), 999L)) {
      assertThatThrownBy(() -> service.create(ADMIN_ID, "Produit", id, List.of()))
          .isInstanceOfSatisfying(
              BusinessException.class, e -> assertThat(e.getRgCode()).isEqualTo("RG-05-02"));
    }
  }

  @Test
  @DisplayName("RG-05-06 : un manager qui l'est déjà d'une autre équipe peut en créer une autre")
  void managerOfSeveralTeams() {
    User m = manager();
    // l'équipe existante du même manager n'est un obstacle à aucun contrôle
    when(teams.findActiveNamesByManagerId(m.getId())).thenReturn(List.of("Technique"));

    TeamDetails created = service.create(ADMIN_ID, "Produit", m.getId(), List.of());

    assertThat(created.team().getManager()).isSameAs(m);
  }

  @Test
  @DisplayName("RG-05-03 : un membre ADMIN est refusé (champ memberIds)")
  void adminCannotBeMember() {
    User m = manager();
    User admin = account(13L, "Adam", "Admin", Role.ADMIN, true);

    assertThatThrownBy(() -> service.create(ADMIN_ID, "Produit", m.getId(), List.of(admin.getId())))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getRgCode()).isEqualTo("RG-05-03");
              assertThat(e.getErrors()).extracting("field").containsExactly("memberIds");
            });
  }

  @Test
  @DisplayName("RG-05-03 : un compte inconnu est traité comme non éligible")
  void unknownMemberRefused() {
    User m = manager();

    assertThatThrownBy(() -> service.create(ADMIN_ID, "Produit", m.getId(), List.of(999L)))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getRgCode()).isEqualTo("RG-05-03"));
  }

  @Test
  @DisplayName(
      "RG-05-03 / RG-05-07 : membres USER et MANAGER acceptés, y compris un compte inactif")
  void userAndManagerMembersAccepted() {
    User m = manager();
    User u = account(11L, "Uma", "User", Role.USER, true);
    User otherManager = account(14L, "Max", "Autre", Role.MANAGER, true);
    User inactive = account(15L, "Ivan", "Inactif", Role.USER, false);

    TeamDetails created =
        service.create(
            ADMIN_ID,
            "Produit",
            m.getId(),
            List.of(u.getId(), otherManager.getId(), inactive.getId()));

    assertThat(created.members()).hasSize(3);
  }

  @Test
  @DisplayName("RG-05-07 : une équipe peut n'avoir aucun membre")
  void teamWithoutMembers() {
    User m = manager();

    TeamDetails created = service.create(ADMIN_ID, "Vide", m.getId(), List.of());

    assertThat(created.members()).isEmpty();
  }

  @Test
  @DisplayName("RG-05-05 : le manager ne peut pas être membre de sa propre équipe")
  void managerCannotBeMember() {
    User m = manager();

    assertThatThrownBy(() -> service.create(ADMIN_ID, "Produit", m.getId(), List.of(m.getId())))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getRgCode()).isEqualTo("RG-05-05");
              assertThat(e.getErrors()).extracting("field").containsExactly("memberIds");
            });
  }

  @Test
  @DisplayName("RG-05-05 : le manager d'une équipe peut être membre d'une autre équipe")
  void managerCanBeMemberElsewhere() {
    User m = manager();
    User otherManager = account(14L, "Max", "Autre", Role.MANAGER, true);

    TeamDetails created =
        service.create(ADMIN_ID, "Produit", m.getId(), List.of(otherManager.getId()));

    assertThat(created.members()).containsExactly(otherManager);
  }

  @Test
  @DisplayName("RG-05-04 : un compte déjà membre d'une autre équipe est refusé, équipe nommée")
  void memberOfAnotherTeamRefused() {
    User m = manager();
    User u = account(11L, "Uma", "User", Role.USER, true);
    Team a = new Team("Équipe A", m);
    setId(a, Team.class, 7L);
    TeamMember link = new TeamMember(u.getId(), 7L);
    setField(link, "team", a);
    when(members.findWithTeamByUserIds(anyCollection())).thenReturn(List.of(link));

    assertThatThrownBy(() -> service.create(ADMIN_ID, "Équipe B", m.getId(), List.of(u.getId())))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getRgCode()).isEqualTo("RG-05-04");
              assertThat(e.getMessage()).contains("Équipe A").contains("Uma User");
              assertThat(e.getErrors()).extracting("field").containsExactly("memberIds");
            });
    verify(members, never()).saveAllAndFlush(any());
  }

  @Test
  @DisplayName("RG-05-04 : un identifiant de membre répété ne crée qu'un rattachement")
  void duplicateMemberIdsAreMerged() {
    User m = manager();
    User u = account(11L, "Uma", "User", Role.USER, true);

    TeamDetails created =
        service.create(ADMIN_ID, "Produit", m.getId(), List.of(u.getId(), u.getId()));

    assertThat(created.members()).hasSize(1);
  }

  @Test
  @DisplayName("RG-05-13 : les membres renvoyés sont triés par nom puis prénom")
  void membersSorted() {
    User m = manager();
    User b = account(11L, "Zoé", "Bernard", Role.USER, true);
    User a2 = account(12L, "Paul", "Adam", Role.USER, true);
    User a1 = account(13L, "Anne", "adam", Role.USER, true);

    TeamDetails created =
        service.create(ADMIN_ID, "Produit", m.getId(), List.of(b.getId(), a2.getId(), a1.getId()));

    assertThat(created.members()).containsExactly(a1, a2, b);
  }

  @Test
  @DisplayName("RG-05-09 : une équipe supprimée ou inconnue est introuvable")
  void unknownTeamNotFound() {
    when(teams.findActiveById(anyLong())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(42L)).isInstanceOf(ResourceNotFoundException.class);
  }

  private static void setField(Object target, String name, Object value) {
    try {
      var f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
