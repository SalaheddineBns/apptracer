package com.apptracer.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * RG-00-04 : traçabilité (qui, quand) de la création et de la dernière modification. {@code
 * createdBy} / {@code updatedBy} sont nuls pour les lignes créées par le système.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private Long createdBy;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @LastModifiedBy
  @Column(name = "updated_by")
  private Long updatedBy;

  /**
   * Marque explicitement l'entité comme modifiée (RG-00-04) quand le changement porte sur des
   * données hors de l'entité, par exemple ses rattachements.
   */
  public void touch(Instant at, Long by) {
    this.updatedAt = at;
    this.updatedBy = by;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Long getCreatedBy() {
    return createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Long getUpdatedBy() {
    return updatedBy;
  }
}
