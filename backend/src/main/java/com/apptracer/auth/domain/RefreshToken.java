package com.apptracer.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Refresh token stored on the server (RG-03-07). Only the SHA-256 of the opaque token is kept.
 * Technical table: it carries its own dates, not the audit columns.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "session_id", nullable = false)
  private UUID sessionId;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "revocation_reason", length = 20)
  private RevocationReason revocationReason;

  protected RefreshToken() {}

  public RefreshToken(
      Long userId, UUID sessionId, String tokenHash, Instant issuedAt, Instant expiresAt) {
    this.userId = userId;
    this.sessionId = sessionId;
    this.tokenHash = tokenHash;
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public RevocationReason getRevocationReason() {
    return revocationReason;
  }

  public void revoke(Instant at, RevocationReason reason) {
    this.revokedAt = at;
    this.revocationReason = reason;
  }
}
