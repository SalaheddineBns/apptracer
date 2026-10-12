package com.apptracer.referential.repository;

import com.apptracer.referential.domain.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, Long> {

  // RG-02-01 : unicité du code dans le référentiel des projets
  // RG-02-10 : tri alphabétique par nom, sans tenir compte de la casse
  @Query("select p from Project p order by lower(p.name), p.code")
  List<Project> findAllSorted();

  // RG-02-06, RG-02-09 : éléments actifs seulement
  @Query("select p from Project p where p.active = true order by lower(p.name), p.code")
  List<Project> findActiveSorted();

  boolean existsByCode(String code);

  Optional<Project> findByCode(String code);
}
