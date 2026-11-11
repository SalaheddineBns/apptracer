package com.apptracer.referential.service;

import com.apptracer.common.error.BusinessException;
import com.apptracer.common.error.ResourceNotFoundException;
import com.apptracer.referential.domain.Project;
import com.apptracer.referential.repository.ProjectRepository;
import com.apptracer.team.domain.Team;
import com.apptracer.team.repository.TeamRepository;
import com.apptracer.team.service.TeamProjectScope;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

  /** Impossible team identifier: replaces an empty list in the {@code in} clause. */
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

  /** RG-02-08: ADMIN only (checked by the controller). */
  @Transactional
  public Project create(String code, String name) {
    return create(code, name, null);
  }

  /** RG-10-01, RG-10-02: {@code teamId} is optional; when absent, the project has no team. */
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
   * RG-02-01: the code cannot be changed. RG-02-08: ADMIN only. RG-10-02: the team can be changed
   * or removed at any time ({@code teamId} absent = no team); RG-10-06: existing time entries do
   * not change; RG-10-10: recorded by JPA auditing.
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
   * RG-02-05: never deleted, only deactivated. No effect if the project is already inactive.
   * RG-02-08: ADMIN only (checked by the controller).
   */
  @Transactional
  public Project deactivate(Long id) {
    return setActive(id, false);
  }

  /** RG-02-05: can be reactivated. No effect if the project is already active. */
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
   * RG-02-09: an ADMIN sees active and inactive ones, the others only the active ones (RG-02-06);
   * sorted alphabetically by name (RG-02-10).
   */
  @Transactional(readOnly = true)
  public List<Project> list(boolean admin) {
    return admin ? projects.findAllSorted() : projects.findActiveSorted();
  }

  /**
   * RG-10-03: an ADMIN sees every project; a USER or a MANAGER sees the projects allowed for their
   * time entries (no team, the team they belong to, the teams they manage). RG-10-09: sorted by
   * name.
   */
  @Transactional(readOnly = true)
  public List<Project> listFor(Long userId, boolean admin) {
    if (admin) {
      return projects.findAllSorted();
    }
    Set<Long> teamIds = teamScope.teamIdsOf(userId);
    return projects.findAllowedSorted(teamIds.isEmpty() ? Set.of(NO_TEAM) : teamIds);
  }

  /** Projects of one team, for "My projects". */
  public record TeamProjects(Team team, List<Project> projects) {}

  /** "My projects": one group per team, then the shared projects. */
  public record MyProjects(List<TeamProjects> teams, List<Project> sharedProjects) {}

  /**
   * RG-10-11: one group per team the account belongs to or manages (teams sorted by name,
   * RG-05-13), with its active projects, then the active projects without a team; projects are
   * sorted by name (RG-10-09). These are exactly the allowed projects (RG-10-03).
   */
  @Transactional(readOnly = true)
  public MyProjects mine(Long userId) {
    Set<Long> teamIds = teamScope.teamIdsOf(userId);
    List<Project> allowed =
        projects.findAllowedSorted(teamIds.isEmpty() ? Set.of(NO_TEAM) : teamIds);
    List<TeamProjects> groups =
        teams.findAllById(teamIds).stream()
            .filter(team -> team.getDeletedAt() == null)
            .sorted(
                Comparator.comparing((Team team) -> team.getName().toLowerCase(Locale.ROOT))
                    .thenComparing(Team::getId))
            .map(
                team ->
                    new TeamProjects(
                        team,
                        allowed.stream()
                            .filter(
                                project ->
                                    project.getTeam() != null
                                        && project.getTeam().getId().equals(team.getId()))
                            .toList()))
            .toList();
    List<Project> shared = allowed.stream().filter(project -> project.getTeam() == null).toList();
    return new MyProjects(groups, shared);
  }

  /** RG-10-01: an existing (not deleted) team, or none. */
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
