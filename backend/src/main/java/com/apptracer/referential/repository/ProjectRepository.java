package com.apptracer.referential.repository;

import com.apptracer.referential.domain.Project;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

  // RG-02-01 : unicité du code dans le référentiel des projets
  boolean existsByCode(String code);

  Optional<Project> findByCode(String code);
}
