package com.apptracer.timeentry.repository;

import com.apptracer.timeentry.domain.TimeEntry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

  // RG-01-11 : une saisie supprimée logiquement n'est plus lisible ; type et projet chargés pour
  // l'affichage (RG-02-05)
  @Query(
      """
      select e from TimeEntry e
      join fetch e.activityType
      left join fetch e.project
      where e.id = :id and e.deletedAt is null
      """)
  Optional<TimeEntry> findActiveById(Long id);
}
