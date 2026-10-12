package com.apptracer.referential.api;

import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.domain.Project;
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

  public ProjectResponse toResponse(Project project) {
    return new ProjectResponse(
        project.getId(),
        project.getCode(),
        project.getName(),
        project.isActive(),
        project.getVersion());
  }
}
