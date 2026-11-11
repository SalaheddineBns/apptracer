package com.apptracer.team.repository;

import com.apptracer.team.domain.TeamMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

  // RG-05-13: members sorted by last name then first name
  @Query(
      """
      select m from TeamMember m join fetch m.user u
      where m.teamId in :teamIds
      order by lower(u.lastName), lower(u.firstName), u.id
      """)
  List<TeamMember> findByTeamIdsSorted(@Param("teamIds") Collection<Long> teamIds);

  // RG-05-04: current team of the given accounts
  @Query(
      """
      select m from TeamMember m join fetch m.team
      where m.userId in :userIds
      """)
  List<TeamMember> findWithTeamByUserIds(@Param("userIds") Collection<Long> userIds);

  // RG-05-04: current team of every linked account
  @Query("select m from TeamMember m join fetch m.team")
  List<TeamMember> findAllWithTeam();

  Optional<TeamMember> findByUserId(Long userId);

  // RG-05-09: the links of a deleted team disappear
  @Modifying(flushAutomatically = true)
  @Query("delete from TeamMember m where m.teamId = :teamId")
  void deleteByTeamId(@Param("teamId") Long teamId);

  // RG-04-02, RG-04-06: current link of the account to a non-deleted team of this manager
  @Query(
      """
      select m from TeamMember m join fetch m.user join fetch m.team t
      where m.userId = :userId and t.manager.id = :managerId and t.deletedAt is null
      """)
  Optional<TeamMember> findManagedMember(
      @Param("userId") Long userId, @Param("managerId") Long managerId);
}
