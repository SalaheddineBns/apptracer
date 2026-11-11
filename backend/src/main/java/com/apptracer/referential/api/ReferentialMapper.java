package com.apptracer.referential.api;

import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.domain.JobTitle;
import com.apptracer.referential.domain.Project;
import com.apptracer.referential.service.ProjectService;
import com.apptracer.team.api.TeamRef;
import org.springframework.stereotype.Component;

@Component
public class ReferentialMapper {

  public ActivityTypeResponse toResponse(ActivityType type) {
    return new ActivityTypeResponse(
        type.getId(),
        type.getCode(),
        type.getLabel(),
        type.isProjectRequired(),
        type.getColor(),
        type.isActive(),
        type.getVersion());
  }

  public JobTitleResponse toResponse(JobTitle title) {
    return new JobTitleResponse(
        title.getId(), title.getCode(), title.getLabel(), title.isActive(), title.getVersion());
  }

  public MyProjectsResponse toResponse(ProjectService.MyProjects mine) {
    return new MyProjectsResponse(
        mine.teams().stream()
            .map(
                group ->
                    new TeamProjectsResponse(
                        new TeamRef(group.team().getId(), group.team().getName()),
                        group.projects().stream().map(this::toResponse).toList()))
            .toList(),
        mine.sharedProjects().stream().map(this::toResponse).toList());
  }

  public ProjectResponse toResponse(Project project) {
    return new ProjectResponse(
        project.getId(),
        project.getCode(),
        project.getName(),
        project.isActive(),
        project.getTeam() == null
            ? null
            : new TeamRef(project.getTeam().getId(), project.getTeam().getName()),
        project.getVersion());
  }
}
