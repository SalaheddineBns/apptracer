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
   * RG-03-07 : révocation conditionnelle d'un jeton actif et non expiré. Renvoie le nombre de
   * lignes modifiées : 0 si le jeton est inconnu, déjà utilisé, révoqué ou expiré. L'atomicité de
   * la mise à jour garantit l'usage unique même pour deux requêtes simultanées.
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
   * RG-03-08 : révocation d'un jeton encore actif (sans effet s'il est inconnu ou déjà révoqué).
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
   * RG-03-14 : révoque tous les jetons encore actifs d'un compte, dans la transaction de
   * l'appelant.
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
   * RG-03-15 : révoque les jetons actifs d'un compte autres que ceux de la session courante, dans
   * la transaction de l'appelant.
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
