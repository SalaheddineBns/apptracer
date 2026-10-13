package com.apptracer.timeentry.repository;

import com.apptracer.timeentry.domain.TimeEntry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /**
   * RG-01-06 : premier jour de chevauchement entre l'intervalle {@code [from, to)} et les saisies
   * non supprimées du même utilisateur, hors saisie {@code excludedId} (RG-01-12). Date ISO {@code
   * yyyy-MM-dd}. Liste vide s'il n'y a aucun chevauchement. La colonne générée {@code occupied}
   * n'est pas mappée dans l'entité.
   */
  @Query(
      value =
          """
          select to_char(greatest(lower(e.occupied), cast(:from as timestamp)), 'YYYY-MM-DD')
          from time_entries e
          where e.user_id = :userId
            and e.deleted_at is null
            and e.id <> :excludedId
            and e.occupied && tsrange(cast(:from as timestamp), cast(:to as timestamp), '[)')
          order by lower(e.occupied)
          limit 1
          """,
      nativeQuery = true)
  List<String> findFirstOverlapDay(
      @Param("userId") Long userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      @Param("excludedId") Long excludedId);
}
