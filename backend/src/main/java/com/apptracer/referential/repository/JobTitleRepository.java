package com.apptracer.referential.repository;

import com.apptracer.referential.domain.JobTitle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobTitleRepository extends JpaRepository<JobTitle, Long> {

  // RG-09-08: sorted alphabetically by label, case-insensitive
  @Query("select j from JobTitle j order by lower(j.label), j.code")
  List<JobTitle> findAllSorted();

  // RG-09-07 : actives seulement
  @Query("select j from JobTitle j where j.active = true order by lower(j.label), j.code")
  List<JobTitle> findActiveSorted();

  // RG-09-05: case-insensitive uniqueness
  @Query("select count(j) > 0 from JobTitle j where lower(j.code) = lower(:code)")
  boolean existsByCode(@Param("code") String code);

  @Query("select j from JobTitle j where lower(j.code) = lower(:code)")
  Optional<JobTitle> findByCode(@Param("code") String code);
}
