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

  // RG-05-13 : membres triés par nom puis prénom
  @Query(
      """
      select m from TeamMember m join fetch m.user u
      where m.teamId in :teamIds
      order by lower(u.lastName), lower(u.firstName), u.id
      """)
  List<TeamMember> findByTeamIdsSorted(@Param("teamIds") Collection<Long> teamIds);

  // RG-05-04 : équipe actuelle des comptes donnés
  @Query(
      """
      select m from TeamMember m join fetch m.team
      where m.userId in :userIds
      """)
  List<TeamMember> findWithTeamByUserIds(@Param("userIds") Collection<Long> userIds);

  // RG-05-04 : équipe actuelle de tous les comptes rattachés
  @Query("select m from TeamMember m join fetch m.team")
  List<TeamMember> findAllWithTeam();

  Optional<TeamMember> findByUserId(Long userId);

  // RG-05-09 : les rattachements d'une équipe supprimée disparaissent
  @Modifying(flushAutomatically = true)
  @Query("delete from TeamMember m where m.teamId = :teamId")
  void deleteByTeamId(@Param("teamId") Long teamId);
}
