package com.apptracer.teamview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.team.domain.Team;
import com.apptracer.team.repository.TeamRepository;
import com.apptracer.teamview.service.ManagedScope;
import com.apptracer.teamview.service.TeamViewService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManagedTeamsServiceTest {

  private final TeamRepository teams = mock(TeamRepository.class);
  private final TeamViewService service = new TeamViewService(new ManagedScope(teams));

  private static Team team(String name) {
    return new Team(name, new User("m@example.com", "M", "Mgr", "hash", Role.MANAGER));
  }

  @Test
  @DisplayName("RG-04-08 : les équipes du manager du token sont renvoyées dans l'ordre du dépôt")
  void returnsTheTeamsOfTheGivenManager() {
    List<Team> sorted = List.of(team("Alpha"), team("Bravo"));
    when(teams.findActiveByManagerId(7L)).thenReturn(sorted);
    assertThat(service.listMyManagedTeams(7L)).isEqualTo(sorted);
    verify(teams).findActiveByManagerId(7L);
  }

  @Test
  @DisplayName("CA-04-09 / RG-04-01 : un manager sans équipe obtient une liste vide, sans erreur")
  void managerWithoutTeamGetsAnEmptyList() {
    when(teams.findActiveByManagerId(8L)).thenReturn(List.of());
    assertThat(service.listMyManagedTeams(8L)).isEmpty();
  }
}
