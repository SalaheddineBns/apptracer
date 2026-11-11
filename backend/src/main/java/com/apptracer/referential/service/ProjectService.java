package com.apptracer.referential.service;

import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.ResourceNotFoundException;
import com.apptracer.referential.domain.Project;
import com.apptracer.referential.repository.ProjectRepository;
import com.apptracer.team.domain.Team;
import com.apptracer.team.repository.TeamRepository;
import com.apptracer.team.service.TeamProjectScope;
import java.util.List;
import java.util.Set;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

  /** Identifiant d'équipe impossible : remplace une liste vide dans la clause {@code in}. */
  private static final Long NO_TEAM = -1L;

  private final ProjectRepository projects;
  private final TeamRepository teams;
  private final TeamProjectScope teamScope;

  public ProjectService(
      ProjectRepository projects, TeamRepository teams, TeamProjectScope teamScope) {
    this.projects = projects;
    this.teams = teams;
    this.teamScope = teamScope;
  }

  /** RG-02-08 : réservé à l'ADMIN (contrôlé par le contrôleur). */
  @Transactional
  public Project create(String code, String name) {
    return create(code, name, null);
  }

  /** RG-10-01, RG-10-02 : {@code teamId} facultatif ; absent, le projet est sans équipe. */
  @Transactional
  public Project create(String code, String name, Long teamId) {
    // RG-02-01 : code unique dans le référentiel (l'index unique couvre les créations concurrentes
    // ;
    // sa violation est traduite en 409 RG-02-01 par le handler global)
    if (projects.existsByCode(code)) {
      throw BusinessException.onField(
          "RG-02-01", "error.project.code-duplicate", "code", "field.code-duplicate");
    }
    // RG-02-05 : actif à la création ; RG-00-04 : created_by / updated_by via l'audit JPA
    Project project = new Project(code, name.strip());
    project.setTeam(resolveTeam(teamId));
    return projects.saveAndFlush(project);
  }

  @Transactional(readOnly = true)
  public Project get(Long id) {
    return projects
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("error.project.not-found"));
  }

  /**
   * RG-02-01 : le code n'est pas modifiable. RG-02-08 : réservé à l'ADMIN. RG-10-02 : l'équipe se
   * change ou se retire à tout moment ({@code teamId} absent = sans équipe) ; RG-10-06 : les
   * saisies existantes ne changent pas ; RG-10-10 : tracé par l'audit JPA.
   */
  @Transactional
  public Project update(Long id, String name, Long teamId, long version) {
    Project project = get(id);
    // Modification concurrente : la version lue par l'ADMIN n'est plus la version en base
    if (project.getVersion() != version) {
      throw new ObjectOptimisticLockingFailureException(Project.class, id);
    }
    project.setName(name.strip());
    project.setTeam(resolveTeam(teamId));
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

  /**
   * RG-10-03 : un ADMIN voit tous les projets ; un USER ou un MANAGER voit les projets autorisés
   * pour sa saisie (sans équipe, équipe dont il est membre, équipes qu'il dirige). RG-10-09 : tri
   * par nom.
   */
  @Transactional(readOnly = true)
  public List<Project> listFor(Long userId, boolean admin) {
    if (admin) {
      return projects.findAllSorted();
    }
    Set<Long> teamIds = teamScope.teamIdsOf(userId);
    return projects.findAllowedSorted(teamIds.isEmpty() ? Set.of(NO_TEAM) : teamIds);
  }

  /** RG-10-01 : équipe existante (non supprimée), ou aucune. */
  private Team resolveTeam(Long teamId) {
    if (teamId == null) {
      return null;
    }
    return teams
        .findActiveById(teamId)
        .orElseThrow(
            () ->
                BusinessException.onField(
                    "RG-10-01",
                    "error.project.team-unknown",
                    "teamId",
                    "field.project.team-unknown"));
  }
}
