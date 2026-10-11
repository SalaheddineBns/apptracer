package com.apptracer.auth.service;

import com.apptracer.auth.domain.RefreshToken;
import com.apptracer.auth.repository.RefreshTokenRepository;
import com.apptracer.security.JwtProperties;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

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
}
