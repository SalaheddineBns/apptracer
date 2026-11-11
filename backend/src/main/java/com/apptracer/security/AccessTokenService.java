package com.apptracer.security;

import com.apptracer.account.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues access tokens (RG-03-06). */
@Service
public class AccessTokenService {

  public static final String CLAIM_ROLE = "role";
  public static final String CLAIM_SESSION_ID = "sid";

  private final JwtEncoder encoder;
  private final JwtProperties properties;
  private final Clock clock;

  public AccessTokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
    this.encoder = encoder;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * RG-03-06: {@code sub} = the user's identifier, {@code role} = their role, {@code sid} = session
   * identifier (used by RG-03-15), expiry = {@code JWT_ACCESS_TTL}.
   */
  public String issue(Long userId, Role role, UUID sessionId) {
    Instant now = Instant.now(clock);
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(String.valueOf(userId))
            .claim(CLAIM_ROLE, role.name())
            .claim(CLAIM_SESSION_ID, sessionId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(properties.accessTtl()))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
