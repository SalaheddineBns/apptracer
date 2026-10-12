package com.apptracer.referential.service;

import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.ResourceNotFoundException;
import com.apptracer.referential.domain.Project;
import com.apptracer.referential.repository.ProjectRepository;
import java.util.List;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

  private final ProjectRepository projects;

  public ProjectService(ProjectRepository projects) {
    this.projects = projects;
  }

  /** RG-02-08 : réservé à l'ADMIN (contrôlé par le contrôleur). */
  @Transactional
  public Project create(String code, String name) {
    // RG-02-01 : code unique dans le référentiel (l'index unique couvre les créations concurrentes
    // ;
    // sa violation est traduite en 409 RG-02-01 par le handler global)
    if (projects.existsByCode(code)) {
      throw BusinessException.onField(
          "RG-02-01", "Un projet existe déjà avec ce code.", "code", "Ce code est déjà utilisé.");
    }
    // RG-02-05 : actif à la création ; RG-00-04 : created_by / updated_by via l'audit JPA
    return projects.saveAndFlush(new Project(code, name.strip()));
  }

  @Transactional(readOnly = true)
  public Project get(Long id) {
    return projects
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Projet introuvable."));
  }

  /** RG-02-01 : le code n'est pas modifiable. RG-02-08 : réservé à l'ADMIN. */
  @Transactional
  public Project update(Long id, String name, long version) {
    Project project = get(id);
    // Modification concurrente : la version lue par l'ADMIN n'est plus la version en base
    if (project.getVersion() != version) {
      throw new ObjectOptimisticLockingFailureException(Project.class, id);
    }
    project.setName(name.strip());
    // RG-00-04 : updated_at / updated_by via l'audit JPA
    return projects.saveAndFlush(project);
  }

  /**
   * RG-02-05 : jamais supprimé, seulement désactivé. Sans effet si le projet est déjà inactif.
   * RG-02-08 : réservé à l'ADMIN (contrôlé par le contrôleur).
   */
  @Transactional
  public Project deactivate(Long id) {
    return setActive(id, false);
  }

  /** RG-02-05 : réactivable. Sans effet si le projet est déjà actif. */
  @Transactional
  public Project reactivate(Long id) {
    return setActive(id, true);
  }

  private Project setActive(Long id, boolean active) {
    Project project = get(id);
    if (project.isActive() == active) {
      return project;
    }
    project.setActive(active);
    // RG-00-04 : updated_at / updated_by via l'audit JPA
    return projects.saveAndFlush(project);
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
