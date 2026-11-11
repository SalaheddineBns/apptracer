package com.apptracer.account.repository;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  // RG-03-01 : recherche insensible à la casse (index ux_users_email_lower)
  @Query("select u from User u where lower(u.email) = lower(:email)")
  Optional<User> findByEmail(@Param("email") String email);

  @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
  boolean existsByEmail(@Param("email") String email);

  boolean existsByRole(Role role);

  /**
   * RG-05-02, RG-05-03: accounts loaded with a shared lock ({@code FOR SHARE}) by team management,
   * in identifier order. A concurrent {@code UserService.update / deactivate} waits for the
   * transaction to end.
   */
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("select u from User u where u.id in :ids order by u.id")
  List<User> findAllByIdForShare(@Param("ids") Collection<Long> ids);

  /** RG-05-10, RG-05-12: account loaded with an exclusive lock ({@code FOR UPDATE}). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  Optional<User> findByIdForUpdate(@Param("id") Long id);

  /**
   * RG-05-02: manager candidates, active accounts with the given role, sorted by last name then
   * first name.
   */
  @Query(
      """
      select u from User u
      where u.role = :role and u.active = true
      order by lower(u.lastName), lower(u.firstName), u.id
      """)
  List<User> findActiveByRoleSorted(@Param("role") Role role);

  /**
   * RG-05-03: member candidates, active or not, with the given roles, sorted by last name then
   * first name.
   */
  @Query(
      """
      select u from User u
      where u.role in :roles
      order by lower(u.lastName), lower(u.firstName), u.id
      """)
  List<User> findByRolesSorted(@Param("roles") Collection<Role> roles);
}
