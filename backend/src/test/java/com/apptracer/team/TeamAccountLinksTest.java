package com.apptracer.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.team.domain.Team;
import com.apptracer.team.domain.TeamMember;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import com.apptracer.team.service.TeamAccountLinks;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeamAccountLinksTest {

  private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

  private final TeamRepository teams = mock(TeamRepository.class);
  private final TeamMemberRepository members = mock(TeamMemberRepository.class);
  private final TeamAccountLinks links =
      new TeamAccountLinks(teams, members, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("RG-05-10 : les noms des équipes gérées viennent des équipes non supprimées")
  void managedTeamNames() {
    when(teams.findActiveNamesByManagerId(7L)).thenReturn(List.of("Produit"));

    assertThat(links.managedTeamNames(7L)).containsExactly("Produit");
  }

  @Test
  @DisplayName("RG-05-12 / RG-05-14 : le retrait supprime le rattachement et trace l'équipe")
  void removeFromTeamDeletesLinkAndTouchesTeam() {
    Team team = new Team("Produit", new User("m@example.com", "M", "M", "hash", Role.MANAGER));
    TeamMember link = new TeamMember(9L, 5L);
    try {
      var f = TeamMember.class.getDeclaredField("team");
      f.setAccessible(true);
      f.set(link, team);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    when(members.findWithTeamByUserIds(List.of(9L))).thenReturn(List.of(link));

    links.removeFromTeam(1L, 9L);

    verify(members).delete(link);
    verify(teams).saveAndFlush(team);
    assertThat(team.getUpdatedAt()).isEqualTo(NOW);
    assertThat(team.getUpdatedBy()).isEqualTo(1L);
  }

  @Test
  @DisplayName("RG-05-12 : un compte sans équipe n'a rien à retirer")
  void removeFromTeamWithoutMembershipIsNoOp() {
    when(members.findWithTeamByUserIds(List.of(9L))).thenReturn(List.of());

    links.removeFromTeam(1L, 9L);

    verify(members, never()).delete(any());
    verify(teams, never()).saveAndFlush(any());
  }
}
