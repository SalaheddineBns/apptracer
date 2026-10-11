package com.apptracer.account.repository;

import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  // RG-03-01 : recherche insensible à la casse (index ux_users_email_lower)
  @Query("select u from User u where lower(u.email) = lower(:email)")
  Optional<User> findByEmail(@Param("email") String email);

  @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
  boolean existsByEmail(@Param("email") String email);

  boolean existsByRole(Role role);
}
