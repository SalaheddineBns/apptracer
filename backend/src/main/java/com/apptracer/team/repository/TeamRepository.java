package com.apptracer.team.repository;

import com.apptracer.team.domain.Team;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {

  // RG-05-09: a deleted team is never read again
  @Query(
      """
      select t from Team t join fetch t.manager
      where t.id = :id and t.deletedAt is null
      """)
  Optional<Team> findActiveById(@Param("id") Long id);

  // RG-05-13: sorted by name
  @Query(
      """
      select t from Team t join fetch t.manager
      where t.deletedAt is null
      order by lower(t.name), t.id
      """)
  List<Team> findAllActiveSorted();

  // RG-05-01: case-insensitive uniqueness among non-deleted teams (index ux_teams_name_lower),
  // excluding the team itself on update
  @Query(
      """
      select count(t) > 0 from Team t
      where lower(t.name) = lower(:name) and t.deletedAt is null and t.id <> :excludedId
      """)
  boolean existsActiveByName(@Param("name") String name, @Param("excludedId") Long excludedId);

  // RG-05-10: names of the non-deleted teams the account manages
  @Query(
      """
      select t.name from Team t
      where t.manager.id = :managerId and t.deletedAt is null
      order by lower(t.name), t.id
      """)
  List<String> findActiveNamesByManagerId(@Param("managerId") Long managerId);

  // RG-04-02, RG-04-08: non-deleted teams the account manages, sorted by name
  @Query(
      """
      select t from Team t
      where t.manager.id = :managerId and t.deletedAt is null
      order by lower(t.name), t.id
      """)
  List<Team> findActiveByManagerId(@Param("managerId") Long managerId);
}
