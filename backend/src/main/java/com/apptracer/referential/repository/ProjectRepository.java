package com.apptracer.referential.repository;

import com.apptracer.referential.domain.Project;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

  // RG-02-01: code unique within the projects. RG-02-10: sorted alphabetically by name,
  // case-insensitive
  @Query("select p from Project p order by lower(p.name), p.code")
  List<Project> findAllSorted();

  // RG-02-06, RG-02-09: active items only
  @Query("select p from Project p where p.active = true order by lower(p.name), p.code")
  List<Project> findActiveSorted();

  /**
   * RG-10-03: active projects with no team or assigned to one of the given teams; RG-10-09: sorted
   * by name.
   */
  @Query(
      "select p from Project p left join p.team t where p.active = true"
          + " and (t is null or t.id in :teamIds) order by lower(p.name), p.code")
  List<Project> findAllowedSorted(@Param("teamIds") Collection<Long> teamIds);

  /** RG-10-07: projects of a team. */
  List<Project> findByTeamId(Long teamId);

  boolean existsByCode(String code);

  Optional<Project> findByCode(String code);
}
