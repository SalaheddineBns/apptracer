package com.apptracer.auth.repository;

import com.apptracer.auth.domain.RefreshToken;
import com.apptracer.auth.domain.RevocationReason;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * RG-03-07: conditional revocation of an active, unexpired token. Returns the number of updated
   * rows: 0 if the token is unknown, already used, revoked or expired. The atomic update guarantees
   * single use even for two concurrent requests.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update RefreshToken t
         set t.revokedAt = :now, t.revocationReason = :reason
       where t.tokenHash = :hash and t.revokedAt is null and t.expiresAt > :now
      """)
  int revokeUsable(
      @Param("hash") String hash,
      @Param("now") Instant now,
      @Param("reason") RevocationReason reason);

  /**
   * RG-03-08: revocation of a still active token (no effect if it is unknown or already revoked).
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update RefreshToken t
         set t.revokedAt = :now, t.revocationReason = :reason
       where t.tokenHash = :hash and t.revokedAt is null
      """)
  int revokeActive(
      @Param("hash") String hash,
      @Param("now") Instant now,
      @Param("reason") RevocationReason reason);

  /**
   * RG-03-14: revokes all the still active tokens of an account, within the caller's transaction.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update RefreshToken t
         set t.revokedAt = :now, t.revocationReason = :reason
       where t.userId = :userId and t.revokedAt is null
      """)
  int revokeAllActiveByUser(
      @Param("userId") Long userId,
      @Param("now") Instant now,
      @Param("reason") RevocationReason reason);

  /**
   * RG-03-15: revokes the active tokens of an account other than those of the current session,
   * within the caller's transaction.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update RefreshToken t
         set t.revokedAt = :now, t.revocationReason = :reason
       where t.userId = :userId and t.sessionId <> :sessionId and t.revokedAt is null
      """)
  int revokeActiveByUserExceptSession(
      @Param("userId") Long userId,
      @Param("sessionId") java.util.UUID sessionId,
      @Param("now") Instant now,
      @Param("reason") RevocationReason reason);
}
