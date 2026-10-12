package com.apptracer.referential.repository;

import com.apptracer.referential.domain.ActivityType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityTypeRepository extends JpaRepository<ActivityType, Long> {

  // RG-02-01 : unicité du code dans le référentiel des types
  boolean existsByCode(String code);

  Optional<ActivityType> findByCode(String code);
}
