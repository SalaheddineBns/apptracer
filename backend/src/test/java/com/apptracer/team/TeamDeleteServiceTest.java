package com.apptracer.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.common.error.ResourceNotFoundException;
import com.apptracer.team.domain.Team;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import com.apptracer.team.service.TeamService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeamDeleteServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

  private final TeamRepository teams = mock(TeamRepository.class);
  private final TeamMemberRepository members = mock(TeamMemberRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final TeamService service =
      new TeamService(
          teams,
          members,
          users,
          Clock.fixed(NOW, ZoneOffset.UTC),
          org.mockito.Mockito.mock(com.apptracer.referential.repository.ProjectRepository.class));

  private Team team() throws Exception {
    Team t = new Team("Produit", new User("m@example.com", "M", "M", "hash", Role.MANAGER));
    var f = Team.class.getDeclaredField("id");
    f.setAccessible(true);
    f.set(t, 5L);
    when(teams.findActiveById(5L)).thenReturn(Optional.of(t));
    return t;
  }

  @Test
  @DisplayName("RG-05-09 : la suppression retire les rattachements puis marque l'équipe")
  void deletesLinksThenMarksTeam() throws Exception {
    Team t = team();

    service.delete(1L, 5L);

    var order = inOrder(members, teams);
    order.verify(members).deleteByTeamId(5L);
    order.verify(teams).saveAndFlush(t);
  }

  @Test
  @DisplayName("RG-05-14 : la suppression est tracée (qui, quand) et la ligne est conservée")
  void deletionIsTraced() throws Exception {
    Team t = team();

    service.delete(1L, 5L);

    assertThat(t.getDeletedBy()).isEqualTo(1L);
    assertThat(t.getDeletedAt()).isEqualTo(NOW);
    assertThat(t.getUpdatedBy()).isEqualTo(1L);
    verify(teams, never()).delete(any(Team.class));
    verify(teams, never()).deleteById(any());
  }

  @Test
  @DisplayName("RG-05-09 : supprimer une équipe inconnue ou déjà supprimée est introuvable")
  void unknownTeam() {
    when(teams.findActiveById(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(1L, 9L)).isInstanceOf(ResourceNotFoundException.class);
    verify(members, never()).deleteByTeamId(any());
  }

  @Test
  @DisplayName("RG-05-09 : la suppression ne touche à aucun compte")
  void accountsAreNotTouched() throws Exception {
    team();

    service.delete(1L, 5L);

    org.mockito.Mockito.verifyNoInteractions(users);
  }
}
