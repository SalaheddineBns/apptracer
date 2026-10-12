package com.apptracer.referential.service;

import com.apptracer.referential.domain.Project;
import com.apptracer.referential.repository.ProjectRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

  private final ProjectRepository projects;

  public ProjectService(ProjectRepository projects) {
    this.projects = projects;
  }

  /**
   * RG-02-09 : un ADMIN voit actifs et inactifs, les autres seulement les actifs (RG-02-06) ; tri
   * alphabétique par nom (RG-02-10).
   */
  @Transactional(readOnly = true)
  public List<Project> list(boolean admin) {
    return admin ? projects.findAllSorted() : projects.findActiveSorted();
  }
}
