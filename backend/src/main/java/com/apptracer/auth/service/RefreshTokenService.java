package com.apptracer.auth.service;

import com.apptracer.account.service.SessionRevoker;
import com.apptracer.auth.domain.RefreshToken;
import com.apptracer.auth.domain.RevocationReason;
import com.apptracer.auth.repository.RefreshTokenRepository;
import com.apptracer.security.JwtProperties;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService implements SessionRevoker {

  private static final int TOKEN_BYTES = 32; // 256 bits

  private final RefreshTokenRepository repository;
  private final JwtProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public RefreshTokenService(
      RefreshTokenRepository repository, JwtProperties properties, Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * RG-03-07 : émet un jeton opaque de 256 bits, stocké haché côté serveur, valable {@code
   * JWT_REFRESH_TTL}. Renvoie le jeton en clair, qui n'existe que dans la réponse.
   */
  public String issue(Long userId, UUID sessionId) {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant now = Instant.now(clock);
    repository.save(
        new RefreshToken(
            userId,
            sessionId,
            TokenHasher.sha256Hex(token),
            now,
            now.plus(properties.refreshTtl())));
    return token;
  }

  /**
   * RG-03-07 : consomme un refresh token (usage unique). Renvoie le jeton consommé, ou vide s'il
   * est inconnu, déjà utilisé, révoqué ou expiré.
   */
  public Optional<RefreshToken> consume(String rawToken) {
    String hash = TokenHasher.sha256Hex(rawToken);
    int updated = repository.revokeUsable(hash, Instant.now(clock), RevocationReason.ROTATED);
    if (updated == 0) {
      return Optional.empty();
    }
    return repository.findByTokenHash(hash);
  }

  /** RG-03-08 : révoque le refresh token présenté ; idempotent. */
  public void revoke(String rawToken) {
    repository.revokeActive(
        TokenHasher.sha256Hex(rawToken), Instant.now(clock), RevocationReason.LOGOUT);
  }

  /** RG-03-14 : désactivation d'un compte. */
  @Override
  public void revokeAllOnDeactivation(Long userId) {
    repository.revokeAllActiveByUser(
        userId, Instant.now(clock), RevocationReason.ACCOUNT_DEACTIVATED);
  }

  /** RG-03-14 : changement de rôle d'un compte. */
  @Override
  public void revokeAllOnRoleChange(Long userId) {
    repository.revokeAllActiveByUser(userId, Instant.now(clock), RevocationReason.ROLE_CHANGED);
  }

  /**
   * RG-03-15 : change de mot de passe, révoque les autres sessions du compte. Sans session courante
   * connue, toutes les sessions sont révoquées.
   */
  public void revokeOtherSessions(Long userId, UUID currentSessionId) {
    Instant now = Instant.now(clock);
    if (currentSessionId == null) {
      repository.revokeAllActiveByUser(userId, now, RevocationReason.PASSWORD_CHANGED);
    } else {
      repository.revokeActiveByUserExceptSession(
          userId, currentSessionId, now, RevocationReason.PASSWORD_CHANGED);
    }
  }
}
