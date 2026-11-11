package com.apptracer.referential.repository;

import com.apptracer.referential.domain.Project;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

  // RG-02-01 : unicité du code dans le référentiel des projets
  // RG-02-10 : tri alphabétique par nom, sans tenir compte de la casse
  @Query("select p from Project p order by lower(p.name), p.code")
  List<Project> findAllSorted();

  // RG-02-06, RG-02-09 : éléments actifs seulement
  @Query("select p from Project p where p.active = true order by lower(p.name), p.code")
  List<Project> findActiveSorted();

  /**
   * RG-10-03 : projets actifs sans équipe ou affectés à l'une des équipes données ; RG-10-09 : tri
   * par nom.
   */
  @Query(
      "select p from Project p left join p.team t where p.active = true"
          + " and (t is null or t.id in :teamIds) order by lower(p.name), p.code")
  List<Project> findAllowedSorted(@Param("teamIds") Collection<Long> teamIds);

  /** RG-10-07 : projets d'une équipe. */
  List<Project> findByTeamId(Long teamId);

  boolean existsByCode(String code);

  Optional<Project> findByCode(String code);
}
