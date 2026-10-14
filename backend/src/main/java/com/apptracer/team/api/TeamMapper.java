package com.apptracer.team.api;

import com.apptracer.account.domain.User;
import com.apptracer.team.domain.Team;
import com.apptracer.team.service.TeamService.MemberCandidate;
import com.apptracer.team.service.TeamService.TeamDetails;
import org.springframework.stereotype.Component;

@Component
public class TeamMapper {

  public TeamResponse toResponse(TeamDetails details) {
    return new TeamResponse(
        details.team().getId(),
        details.team().getName(),
        toSummary(details.team().getManager()),
        details.members().stream().map(this::toSummary).toList(),
        details.members().size(),
        details.team().getVersion());
  }

  public TeamAccountSummary toSummary(User user) {
    return new TeamAccountSummary(
        user.getId(),
        user.getFirstName(),
        user.getLastName(),
        user.getEmail(),
        user.getRole(),
        user.isActive());
  }

  public TeamMemberCandidateResponse toCandidate(MemberCandidate candidate) {
    User user = candidate.user();
    Team current = candidate.currentTeam();
    return new TeamMemberCandidateResponse(
        user.getId(),
        user.getFirstName(),
        user.getLastName(),
        user.getEmail(),
        user.getRole(),
        user.isActive(),
        current == null ? null : new TeamRef(current.getId(), current.getName()));
  }
}
