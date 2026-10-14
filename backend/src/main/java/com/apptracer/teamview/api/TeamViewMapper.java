package com.apptracer.teamview.api;

import com.apptracer.team.domain.Team;
import org.springframework.stereotype.Component;

@Component
public class TeamViewMapper {

  public ManagedTeamResponse toResponse(Team t) {
    return new ManagedTeamResponse(t.getId(), t.getName());
  }
}
