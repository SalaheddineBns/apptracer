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
   * RG-01-06: first day of overlap between the interval {@code [from, to)} and the same user's
   * non-deleted time entries, excluding entry {@code excludedId} (RG-01-12). ISO date {@code
   * yyyy-MM-dd}. Empty list when there is no overlap. The generated column {@code occupied} is not
   * mapped in the entity.
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

  /**
   * RG-02-07: true as soon as a time entry references the type, logically deleted entries included
   * (architecture, H3): no filter on {@code deletedAt}.
   */
  boolean existsByActivityTypeId(Long activityTypeId);

  boolean existsByUserId(Long userId);

  /**
   * RG-01-11, RG-01-13: non-deleted time entries of an account whose period intersects {@code
   * [weekStart, weekEnd]} (a RANGE spanning the week boundary is therefore included). Type and
   * project are loaded in the same query, active or not (RG-02-05). Order: start date, then start
   * time (entries without a time first), then identifier.
   */
  @Query(
      """
      select e from TimeEntry e
      join fetch e.activityType
      left join fetch e.project
      where e.userId = :userId
        and e.deletedAt is null
        and e.startDate <= :weekEnd
        and e.endDate >= :weekStart
      order by e.startDate, e.startTime nulls first, e.id
      """)
  List<TimeEntry> findWeek(
      @Param("userId") Long userId,
      @Param("weekStart") java.time.LocalDate weekStart,
      @Param("weekEnd") java.time.LocalDate weekEnd);

  /** RG-04-05: same read as {@link #findWeek} for several accounts in one query (team view). */
  @Query(
      """
      select e from TimeEntry e
      join fetch e.activityType
      left join fetch e.project
      where e.userId in :userIds
        and e.deletedAt is null
        and e.startDate <= :weekEnd
        and e.endDate >= :weekStart
      order by e.startDate, e.startTime nulls first, e.id
      """)
  List<TimeEntry> findWeekForUsers(
      @Param("userIds") java.util.Collection<Long> userIds,
      @Param("weekStart") java.time.LocalDate weekStart,
      @Param("weekEnd") java.time.LocalDate weekEnd);
}
