package com.apptracer.referential.repository;

import com.apptracer.referential.domain.ActivityType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ActivityTypeRepository extends JpaRepository<ActivityType, Long> {

  // RG-02-01: code unique within the activity types. RG-02-10: sorted alphabetically by label,
  // case-insensitive
  @Query("select t from ActivityType t order by lower(t.label), t.code")
  List<ActivityType> findAllSorted();

  // RG-02-06, RG-02-09: active items only
  @Query("select t from ActivityType t where t.active = true order by lower(t.label), t.code")
  List<ActivityType> findActiveSorted();

  boolean existsByCode(String code);

  Optional<ActivityType> findByCode(String code);
}
